package com.loveos.api.dailyquestions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.memories.MemoriesService;
import com.loveos.api.pairing.domain.Couple;
import com.loveos.api.pairing.domain.CoupleMember;
import com.loveos.api.pairing.domain.CoupleRole;
import com.loveos.api.pairing.domain.CoupleStatus;
import com.loveos.api.pairing.repo.CoupleMemberRepository;
import com.loveos.api.pairing.repo.CoupleRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
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
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "loveos.jwt.secret=test-only-secret-with-at-least-32-characters",
    "loveos.mail.driver=console", "spring.flyway.baseline-on-migrate=false"
})
class DailyQuestionPostgresIntegrationTest {

  @Autowired private DailyQuestionService service;
  @Autowired private MemoriesService memories;
  @Autowired private UserRepository users;
  @Autowired private CoupleRepository couples;
  @Autowired private CoupleMemberRepository members;

  private User founder;
  private User partner;
  private Couple couple;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString();
    founder = user("daily-founder-" + suffix + "@example.test", "Alex");
    partner = user("daily-partner-" + suffix + "@example.test", "Sam");
    couple = new Couple();
    couple.setStatus(CoupleStatus.CONNECTED);
    couples.saveAndFlush(couple);
    member(founder, CoupleRole.FOUNDER);
    member(partner, CoupleRole.MEMBER);
  }

  @Test
  void answersStayMaskedUntilBothAnswerThenCreateOneMemory() {
    var today = service.today(founder.getId());
    var first = service.answer(founder.getId(),
        new DailyQuestionDtos.SubmitAnswer("Coffee together."));

    assertThat(first.status()).isEqualTo("waiting");
    assertThat(first.myAnswer()).isEqualTo("Coffee together.");
    assertThat(first.partnerAnswer()).isNull();

    var partnerView = service.today(partner.getId());
    assertThat(partnerView.id()).isEqualTo(today.id());
    assertThat(partnerView.partnerHasAnswered()).isTrue();
    assertThat(partnerView.partnerAnswer()).isNull();

    var completed = service.answer(partner.getId(),
        new DailyQuestionDtos.SubmitAnswer("Our evening walk."));
    assertThat(completed.status()).isEqualTo("complete");
    assertThat(completed.partnerAnswer()).isEqualTo("Coffee together.");
    assertThat(completed.memoryId()).isNotNull();

    var founderComplete = service.today(founder.getId());
    assertThat(founderComplete.partnerAnswer()).isEqualTo("Our evening walk.");
    assertThat(founderComplete.memoryId()).isEqualTo(completed.memoryId());
    assertThat(memories.list(founder.getId(), null, 50, "daily questions", null, null, null)
        .items()).singleElement().satisfies(memory -> {
          assertThat(memory.caption()).isEqualTo(today.prompt());
          assertThat(memory.note()).contains("Alex:", "Sam:", "Coffee together.", "Our evening walk.");
        });

    service.answer(founder.getId(), new DailyQuestionDtos.SubmitAnswer("Tea together."));
    assertThat(memories.list(founder.getId(), null, 50, "daily questions", null, null, null)
        .items()).singleElement().extracting(memory -> memory.note()).asString()
        .contains("Tea together.").doesNotContain("Coffee together.");
  }

  @Test
  void simultaneousReadsCreateOneAssignment() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var one = executor.submit(() -> { start.await(); return service.today(founder.getId()); });
      var two = executor.submit(() -> { start.await(); return service.today(partner.getId()); });
      start.countDown();
      assertThat(one.get().id()).isEqualTo(two.get().id());
    }
  }

  @Test
  void simultaneousAnswersCreateExactlyOneMemory() throws Exception {
    service.today(founder.getId());
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var one = executor.submit(() -> {
        start.await();
        return service.answer(founder.getId(),
            new DailyQuestionDtos.SubmitAnswer("Founder answer"));
      });
      var two = executor.submit(() -> {
        start.await();
        return service.answer(partner.getId(),
            new DailyQuestionDtos.SubmitAnswer("Partner answer"));
      });
      start.countDown();
      one.get();
      two.get();
    }

    assertThat(service.today(founder.getId()).status()).isEqualTo("complete");
    assertThat(memories.list(founder.getId(), null, 50, "daily questions", null, null, null)
        .items()).hasSize(1);
  }

  @Test
  void pausedCoupleCannotAnswer() {
    couple.setStatus(CoupleStatus.PAUSED);
    couples.saveAndFlush(couple);

    assertThatThrownBy(() -> service.answer(founder.getId(),
        new DailyQuestionDtos.SubmitAnswer("Blocked")))
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
