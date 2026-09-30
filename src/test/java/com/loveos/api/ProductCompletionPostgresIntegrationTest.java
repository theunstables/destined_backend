package com.loveos.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.loveos.api.account.AccountService;
import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.notifications.DevicePlatform;
import com.loveos.api.notifications.NotificationDtos;
import com.loveos.api.notifications.NotificationService;
import com.loveos.api.pairing.domain.Couple;
import com.loveos.api.pairing.domain.CoupleMember;
import com.loveos.api.pairing.domain.CoupleRole;
import com.loveos.api.pairing.domain.CoupleStatus;
import com.loveos.api.pairing.repo.CoupleMemberRepository;
import com.loveos.api.pairing.repo.CoupleRepository;
import com.loveos.api.timeline.TimelineService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "loveos.jwt.secret=test-only-secret-with-at-least-32-characters",
    "loveos.mail.driver=console", "spring.flyway.baseline-on-migrate=false"
})
class ProductCompletionPostgresIntegrationTest {

  @Autowired private NotificationService notifications;
  @Autowired private AccountService accounts;
  @Autowired private TimelineService timeline;
  @Autowired private UserRepository users;
  @Autowired private CoupleRepository couples;
  @Autowired private CoupleMemberRepository members;
  @Autowired private JdbcClient jdbc;

  private User user;
  private Couple couple;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setEmail("phase6-" + UUID.randomUUID() + "@example.test");
    user.setEmailVerified(true);
    user.setDisplayName("Alex");
    users.saveAndFlush(user);

    couple = new Couple();
    couple.setStatus(CoupleStatus.CONNECTED);
    couples.saveAndFlush(couple);
    CoupleMember member = new CoupleMember();
    member.setCoupleId(couple.getId());
    member.setUserId(user.getId());
    member.setRole(CoupleRole.FOUNDER);
    members.saveAndFlush(member);
  }

  @Test
  void preferencesDevicesDeletionAndExportRoundTrip() {
    assertThat(notifications.getPreferences(user.getId()).timezone()).isEqualTo("UTC");
    var updated = notifications.updatePreferences(user.getId(),
        new NotificationDtos.UpdatePreferences(
            true, false, true, true, "22:00", "07:00", "America/New_York"));
    assertThat(updated.messages()).isFalse();
    assertThat(updated.quietStart()).isEqualTo("22:00");
    assertThat(notifications.register(user.getId(),
        new NotificationDtos.RegisterDevice("ExponentPushToken[test]", DevicePlatform.EXPO))
        .platform()).isEqualTo("expo");

    var deletion = accounts.requestDeletion(user.getId());
    assertThat(deletion.pending()).isTrue();
    assertThat(deletion.executeAfter()).isNotBlank();
    assertThat(accounts.cancelDeletion(user.getId()).pending()).isFalse();

    var exported = accounts.export(user.getId());
    assertThat(exported.data()).containsKeys("profile", "couple", "members", "messages");
  }

  @Test
  void timelineComposesSourcesAndPaginatesWithoutOwningData() {
    jdbc.sql("""
        insert into memories (couple_id, author_id, title, date)
        values (:coupleId, :userId, 'First memory', date '2025-01-02'),
               (:coupleId, :userId, 'Older memory', date '2025-01-01')
        """).param("coupleId", couple.getId()).param("userId", user.getId()).update();
    jdbc.sql("""
        insert into calendar_events (couple_id, created_by_id, title, date)
        values (:coupleId, :userId, 'Date night', date '2025-01-03')
        """).param("coupleId", couple.getId()).param("userId", user.getId()).update();

    var first = timeline.list(user.getId(), null, 2);
    assertThat(first.items()).extracting(TimelineService.Item::title)
        .containsExactly("Date night", "First memory");
    assertThat(first.nextCursor()).isNotNull();
    assertThat(timeline.list(user.getId(), first.nextCursor(), 2).items())
        .singleElement().extracting(TimelineService.Item::title).isEqualTo("Older memory");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class PostgresTestConfiguration {
    @Bean(destroyMethod = "close")
    EmbeddedPostgres testPostgres() throws IOException {
      return EmbeddedPostgres.builder().setPort(0).start();
    }

    @Bean
    @Primary
    DataSource dataSource(EmbeddedPostgres postgres) {
      return postgres.getPostgresDatabase();
    }
  }
}
