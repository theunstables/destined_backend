package com.loveos.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.messaging.dto.MessagingRequests;
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
class MessagingPostgresIntegrationTest {
  @Autowired private MessagingService service;
  @Autowired private UserRepository users;
  @Autowired private CoupleRepository couples;
  @Autowired private CoupleMemberRepository members;

  private User founder;
  private User partner;
  private Couple couple;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString();
    founder = user("message-founder-" + suffix + "@example.test", "Alex");
    partner = user("message-partner-" + suffix + "@example.test", "Sam");
    couple = new Couple();
    couple.setStatus(CoupleStatus.CONNECTED);
    couples.saveAndFlush(couple);
    member(couple, founder, CoupleRole.FOUNDER);
    member(couple, partner, CoupleRole.MEMBER);
  }

  @Test
  void sendIsIdempotentAndSerializesRelativeToViewer() {
    var input = new MessagingRequests.SendMessage(
        "text", " Hello ", null, null, null, "device-message-0001");

    var first = service.send(founder.getId(), input);
    var retry = service.send(founder.getId(), input);

    assertThat(first.duplicate()).isFalse();
    assertThat(retry.duplicate()).isTrue();
    assertThat(retry.message().id()).isEqualTo(first.message().id());
    assertThat(service.list(founder.getId(), null, 50).items())
        .singleElement().satisfies(message -> {
          assertThat(message.authorId()).isEqualTo("me");
          assertThat(message.body()).isEqualTo("Hello");
          assertThat(message.status()).isEqualTo("sent");
        });
    assertThat(service.list(partner.getId(), null, 50).items())
        .singleElement().extracting(message -> message.authorId()).isEqualTo("partner");
  }

  @Test
  void reactionsPinsReadReceiptsAndSoftDeleteRoundTrip() {
    var sent = service.send(founder.getId(), new MessagingRequests.SendMessage(
        "text", "Private", null, null, null, "device-message-0002"));
    UUID id = sent.messageId();

    assertThat(service.toggleReaction(partner.getId(), id, "❤️").reactions())
        .singleElement().extracting(reaction -> reaction.authorId()).isEqualTo("me");
    assertThat(service.togglePin(partner.getId(), id).pinned()).isTrue();
    assertThat(service.markDelivered(partner.getId())).containsExactly(id.toString());
    assertThat(service.markRead(partner.getId(), id).messageIds()).containsExactly(id.toString());
    assertThat(service.list(founder.getId(), null, 50).items())
        .singleElement().extracting(message -> message.status()).isEqualTo("read");

    service.delete(founder.getId(), id);
    assertThat(service.list(founder.getId(), null, 50).items()).isEmpty();
  }

    @Test
    void pausedCoupleCanReadHistoryButCannotWrite() {
    service.send(founder.getId(), new MessagingRequests.SendMessage(
      "text", "Keep this", null, null, null, "device-message-paused"));
    couple.setStatus(CoupleStatus.PAUSED);
    couples.saveAndFlush(couple);

    assertThat(service.list(founder.getId(), null, 50).items())
      .singleElement().extracting(message -> message.body()).isEqualTo("Keep this");
    assertThatThrownBy(() -> service.send(founder.getId(), new MessagingRequests.SendMessage(
      "text", "Blocked", null, null, null, "device-message-blocked")))
      .isInstanceOfSatisfying(AppException.class,
        error -> assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }

  @Test
  void messageIdsCannotCrossCoupleBoundary() {
    var sent = service.send(founder.getId(), new MessagingRequests.SendMessage(
        "text", "Private", null, null, null, "device-message-0003"));
    User stranger = user("message-stranger-" + UUID.randomUUID() + "@example.test", "Taylor");
    User strangerPartner = user("message-stranger-partner-" + UUID.randomUUID()
        + "@example.test", "Jordan");
    Couple other = new Couple();
    other.setStatus(CoupleStatus.CONNECTED);
    couples.saveAndFlush(other);
    member(other, stranger, CoupleRole.FOUNDER);
    member(other, strangerPartner, CoupleRole.MEMBER);

    assertThatThrownBy(() -> service.togglePin(stranger.getId(), sent.messageId()))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  void cursorSearchReplyAndSenderOnlyDeleteAreEnforced() {
    var first = service.send(founder.getId(), new MessagingRequests.SendMessage(
        "text", "First searchable note", null, null, null, "device-message-1001"));
    var second = service.send(partner.getId(), new MessagingRequests.SendMessage(
        "photo", "A caption", "https://example.test/photo.jpg", null,
        first.messageId().toString(), "device-message-1002"));
    service.send(founder.getId(), new MessagingRequests.SendMessage(
        "text", "Third", null, null, null, "device-message-1003"));

    var newest = service.list(founder.getId(), null, 2);
    assertThat(newest.items()).hasSize(2);
    assertThat(newest.nextCursor()).isNotNull();
    assertThat(service.list(founder.getId(), newest.nextCursor(), 2).items())
        .singleElement().extracting(message -> message.id()).isEqualTo(first.message().id());
    assertThat(service.search(partner.getId(), "SEARCHABLE", 10))
        .singleElement().extracting(message -> message.id()).isEqualTo(first.message().id());
    assertThat(service.get(founder.getId(), second.messageId()).replyToId())
        .isEqualTo(first.messageId().toString());

    assertThatThrownBy(() -> service.delete(founder.getId(), second.messageId()))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  void concurrentRetriesStoreExactlyOneMessage() throws Exception {
    var input = new MessagingRequests.SendMessage(
        "text", "Once", null, null, null, "device-message-race");
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var one = executor.submit(() -> {
        start.await();
        return service.send(founder.getId(), input);
      });
      var two = executor.submit(() -> {
        start.await();
        return service.send(founder.getId(), input);
      });
      start.countDown();

      assertThat(one.get().messageId()).isEqualTo(two.get().messageId());
      assertThat(service.list(founder.getId(), null, 50).items()).hasSize(1);
    }
  }

  private User user(String email, String displayName) {
    User user = new User();
    user.setEmail(email);
    user.setEmailVerified(true);
    user.setDisplayName(displayName);
    return users.saveAndFlush(user);
  }

  private void member(Couple couple, User user, CoupleRole role) {
    CoupleMember member = new CoupleMember();
    member.setCoupleId(couple.getId());
    member.setUserId(user.getId());
    member.setRole(role);
    members.saveAndFlush(member);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class PostgresTestConfiguration {
    @Bean(destroyMethod = "close") EmbeddedPostgres testPostgres() throws IOException {
      return EmbeddedPostgres.builder().setPort(0).start();
    }
    @Bean @Primary DataSource dataSource(EmbeddedPostgres postgres) {
      return postgres.getPostgresDatabase();
    }
  }
}
