package com.loveos.api.repairsignal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.domain.Couple;
import com.loveos.api.pairing.domain.CoupleMember;
import com.loveos.api.pairing.domain.CoupleRole;
import com.loveos.api.pairing.domain.CoupleStatus;
import com.loveos.api.pairing.repo.CoupleMemberRepository;
import com.loveos.api.pairing.repo.CoupleRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
class RepairSignalPostgresIntegrationTest {

  @Autowired private RepairSignalService service;
  @Autowired private RepairSignalRepository signals;
  @Autowired private UserRepository users;
  @Autowired private CoupleRepository couples;
  @Autowired private CoupleMemberRepository members;
  @Autowired private JdbcClient jdbc;

  private User founder;
  private User partner;
  private Couple couple;

  @BeforeEach
  void setUp() {
    signals.deleteAll();
    String suffix = UUID.randomUUID().toString();
    founder = user("repair-founder-" + suffix + "@example.test", "Alex");
    partner = user("repair-partner-" + suffix + "@example.test", "Sam");
    couple = new Couple();
    couple.setStatus(CoupleStatus.CONNECTED);
    couples.saveAndFlush(couple);
    member(founder, CoupleRole.FOUNDER);
    member(partner, CoupleRole.MEMBER);
  }

  @Test
  void repeatedSendIsIdempotentAndPartnerSignalBecomesMutual() {
    var first = service.send(founder.getId());
    var retry = service.send(founder.getId());
    assertThat(retry.id()).isEqualTo(first.id());
    assertThat(retry.status()).isEqualTo("open");

    var mutual = service.send(partner.getId());
    assertThat(mutual.id()).isEqualTo(first.id());
    assertThat(mutual.mutual()).isTrue();
    assertThat(mutual.sentByMe()).isFalse();
    assertThat(service.current(founder.getId()).mutual()).isTrue();
    assertThat(signals.count()).isEqualTo(1);
  }

  @Test
  void simultaneousPartnerSendsConvergeToOneMutualSignal() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var one = executor.submit(() -> { start.await(); return service.send(founder.getId()); });
      var two = executor.submit(() -> { start.await(); return service.send(partner.getId()); });
      start.countDown();
      var first = one.get();
      var second = two.get();
      assertThat(first.id()).isEqualTo(second.id());
      assertThat(service.current(founder.getId()).mutual()).isTrue();
      assertThat(signals.count()).isEqualTo(1);
    }
  }

  @Test
  void expiryIsQuietAndAllowsANewSignal() {
    var old = service.send(founder.getId());
    jdbc.sql("update repair_signals set created_at = :created, expires_at = :expired where id = :id")
      .param("created", Timestamp.from(Instant.now().minusSeconds(86_401)))
      .param("expired", Timestamp.from(Instant.now().minusSeconds(1)))
        .param("id", UUID.fromString(old.id())).update();

    assertThat(service.current(partner.getId())).isNull();
    var replacement = service.send(partner.getId());
    assertThat(replacement.id()).isNotEqualTo(old.id());
    assertThat(replacement.status()).isEqualTo("open");
  }

  @Test
  void onlySenderCanCancelButCancellationRemainsAvailableWhilePaused() {
    service.send(founder.getId());
    assertThatThrownBy(() -> service.cancel(partner.getId()))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN));

    couple.setStatus(CoupleStatus.PAUSED);
    couples.saveAndFlush(couple);
    service.cancel(founder.getId());
    assertThat(service.current(founder.getId())).isNull();
  }

  @Test
  void pausedCoupleCannotSend() {
    couple.setStatus(CoupleStatus.PAUSED);
    couples.saveAndFlush(couple);
    assertThatThrownBy(() -> service.send(founder.getId()))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN));
  }

  private User user(String email, String name) {
    User user = new User();
    user.setEmail(email);
    user.setEmailVerified(true);
    user.setDisplayName(name);
    return users.saveAndFlush(user);
  }

  private void member(User user, CoupleRole role) {
    CoupleMember member = new CoupleMember();
    member.setCoupleId(couple.getId());
    member.setUserId(user.getId());
    member.setRole(role);
    members.saveAndFlush(member);
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
