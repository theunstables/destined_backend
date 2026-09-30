package com.loveos.api.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.calendar.dto.CalendarRequests;
import com.loveos.api.calendar.repo.CalendarEventRepository;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.domain.Couple;
import com.loveos.api.pairing.domain.CoupleMember;
import com.loveos.api.pairing.domain.CoupleRole;
import com.loveos.api.pairing.repo.CoupleMemberRepository;
import com.loveos.api.pairing.repo.CoupleRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "loveos.jwt.secret=test-only-secret-with-at-least-32-characters",
    "loveos.mail.driver=console",
    "spring.flyway.baseline-on-migrate=false"
})
class CalendarPostgresIntegrationTest {

  @Autowired private CalendarService service;
  @Autowired private UserRepository users;
  @Autowired private CoupleRepository couples;
  @Autowired private CoupleMemberRepository members;
  @Autowired private CalendarEventRepository events;

  private User founder;

  @BeforeEach
  void setUp() {
    founder = user("calendar-" + UUID.randomUUID() + "@example.test", "Founder");
    Couple couple = new Couple();
    couples.saveAndFlush(couple);
    CoupleMember member = new CoupleMember();
    member.setCoupleId(couple.getId());
    member.setUserId(founder.getId());
    member.setRole(CoupleRole.FOUNDER);
    members.saveAndFlush(member);
  }

  @Test
  void createListUpdateAndSoftDeleteRoundTrip() {
    var created = service.create(founder.getId(), new CalendarRequests.CreateEvent(
        " Date night ", "2026-10-01", "2026-10-01T18:00:00Z", null,
        " Rome ", null, "dateNight", false, 60));

    assertThat(service.list(founder.getId(), "2026-10-01", "2026-10-31", 100))
        .singleElement().extracting(item -> item.title()).isEqualTo("Date night");

    var updated = service.update(founder.getId(), UUID.fromString(created.id()),
        new CalendarRequests.UpdateEvent("Dinner", null, null, null, null, null,
            null, null, null));
    assertThat(updated.title()).isEqualTo("Dinner");

    service.delete(founder.getId(), UUID.fromString(created.id()));
    assertThat(service.list(founder.getId(), null, null, 100)).isEmpty();
    assertThat(events.findById(UUID.fromString(created.id())))
        .get().extracting(event -> event.getDeletedAt()).isNotNull();
  }

  @Test
  void eventIdsCannotCrossCoupleBoundary() {
    var created = service.create(founder.getId(), new CalendarRequests.CreateEvent(
        "Private", "2026-10-01", null, null, null, null, null, null, null));
    User stranger = user("stranger-" + UUID.randomUUID() + "@example.test", "Stranger");
    Couple other = new Couple();
    couples.saveAndFlush(other);
    CoupleMember membership = new CoupleMember();
    membership.setCoupleId(other.getId());
    membership.setUserId(stranger.getId());
    membership.setRole(CoupleRole.FOUNDER);
    members.saveAndFlush(membership);

    assertThatThrownBy(() -> service.get(stranger.getId(), UUID.fromString(created.id())))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  void upcomingSkipsPastOneOffEventsAndWrapsAnnualEvents() {
    LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
    service.create(founder.getId(), new CalendarRequests.CreateEvent(
        "Past", today.minusDays(1).toString(), null, null, null, null,
        "custom", false, null));
    service.create(founder.getId(), new CalendarRequests.CreateEvent(
        "Annual", today.minusDays(1).toString(), null, null, null, null,
        "anniversary", true, null));

    var upcoming = service.upcoming(founder.getId(), 400, 10);

    assertThat(upcoming).noneMatch(item -> item.label().equals("Past"));
    assertThat(upcoming).anyMatch(item -> item.label().equals("Annual") && item.days() > 300);
  }

  private User user(String email, String name) {
    User user = new User();
    user.setEmail(email);
    user.setEmailVerified(true);
    user.setDisplayName(name);
    return users.saveAndFlush(user);
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