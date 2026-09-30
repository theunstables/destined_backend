package com.loveos.api.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.memories.repo.AlbumRepository;
import com.loveos.api.pairing.domain.Invite;
import com.loveos.api.pairing.dto.PairingRequests;
import com.loveos.api.pairing.repo.CoupleMemberRepository;
import com.loveos.api.pairing.repo.CoupleRepository;
import com.loveos.api.pairing.repo.InviteRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
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
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "loveos.jwt.secret=test-only-secret-with-at-least-32-characters",
    "loveos.mail.driver=console",
    "spring.flyway.baseline-on-migrate=false"
})
class PairingPostgresIntegrationTest {

  @Autowired private PairingService pairing;
  @Autowired private UserRepository users;
  @Autowired private CoupleRepository couples;
  @Autowired private CoupleMemberRepository members;
  @Autowired private InviteRepository invites;
  @Autowired private AlbumRepository albums;

  @BeforeEach
  void cleanDatabase() {
    invites.deleteAll();
    members.deleteAll();
    couples.deleteAll();
    users.deleteAll();
  }

  @Test
  void completeLifecyclePersistsProfilesStatusesAndSharedSpace() {
    User alex = user("alex@example.test", "Alex");
    User sam = user("sam@example.test", "Sam");

    pairing.saveProfile(alex.getId(), new PairingRequests.ProfileUpdate(
        "Alex", "Al", "they/them", "1997-04-12", "https://example.test/alex.jpg"));
    var issued = pairing.createInvite(alex.getId());
    assertThat(pairing.getSpace(alex.getId()).status()).isEqualTo("inviting");

    var founder = pairing.redeemCode(sam.getId(), issued.code());
    assertThat(founder.name()).isEqualTo("Al");
    assertThat(pairing.getSpace(alex.getId()).status()).isEqualTo("pending");
    assertThat(pairing.getSpace(sam.getId()).status()).isEqualTo("pending");

    pairing.confirmPartner(sam.getId(), alex.getId());
    assertThat(pairing.getSpace(alex.getId()).status()).isEqualTo("connected");
    assertThat(pairing.getSpace(alex.getId()).partner().name()).isEqualTo("Sam");
    assertThat(pairing.getSpace(sam.getId()).partner().name()).isEqualTo("Al");
    assertThat(members.countByCoupleId(couples.findAll().getFirst().getId())).isEqualTo(2);
    assertThat(albums.findByCoupleIdOrderByCreatedAtAsc(couples.findAll().getFirst().getId()))
      .extracting(album -> album.getSystemKey())
      .containsExactly("us", "trips", "dates", "birthdays", "littlethings");

    var updated = pairing.updateSpace(alex.getId(),
        new PairingRequests.UpdateSpace("Our Place", "Us", "night"));
    assertThat(updated.name()).isEqualTo("Our Place");
    assertThat(updated.coverStyle()).isEqualTo("night");
  }

  @Test
  void selfPairingAndExpiredInvitesHaveStableErrors() {
    User alex = user("alex@example.test", "Alex");
    User sam = user("sam@example.test", "Sam");
    var issued = pairing.createInvite(alex.getId());

    assertCode(ErrorCode.CANNOT_PAIR_WITH_SELF,
        () -> pairing.redeemCode(alex.getId(), issued.code()));

    Invite invite = invites.findAll().getFirst();
    invite.setExpiresAt(Instant.now().minusSeconds(1));
    invites.saveAndFlush(invite);
    assertCode(ErrorCode.CODE_EXPIRED,
        () -> pairing.redeemCode(sam.getId(), issued.code()));
  }

  @Test
  void onlyOneConcurrentRedeemerCanClaimAnInvite() throws Exception {
    User founder = user("founder@example.test", "Founder");
    User first = user("first@example.test", "First");
    User second = user("second@example.test", "Second");
    String code = pairing.createInvite(founder.getId()).code();
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstAttempt = executor.submit(() -> redeemAfter(start, first.getId(), code));
      var secondAttempt = executor.submit(() -> redeemAfter(start, second.getId(), code));
      start.countDown();

      int successes = (firstAttempt.get() ? 1 : 0) + (secondAttempt.get() ? 1 : 0);
      assertThat(successes).isEqualTo(1);
    }
  }

  @Test
  void connectedUserCannotCreateAnotherCouple() {
    User alex = user("alex@example.test", "Alex");
    User sam = user("sam@example.test", "Sam");
    String code = pairing.createInvite(alex.getId()).code();
    pairing.redeemCode(sam.getId(), code);
    pairing.confirmPartner(sam.getId(), alex.getId());

    assertCode(ErrorCode.ALREADY_PAIRED, () -> pairing.createInvite(alex.getId()));
    assertThat(members.findByUserId(alex.getId())).isPresent();
    assertThat(couples.count()).isEqualTo(1);
  }

  @Test
  void pauseAndUnpairGracePeriodAreReversibleAndBlockSharedWrites() {
    User alex = user("alex-lifecycle@example.test", "Alex");
    User sam = user("sam-lifecycle@example.test", "Sam");
    String code = pairing.createInvite(alex.getId()).code();
    pairing.redeemCode(sam.getId(), code);
    pairing.confirmPartner(sam.getId(), alex.getId());

    var paused = pairing.pause(alex.getId());
    assertThat(paused.status()).isEqualTo("paused");
    assertThat(paused.sharedWritesAllowed()).isFalse();
    assertCode(ErrorCode.FORBIDDEN, () -> pairing.updateSpace(sam.getId(),
        new PairingRequests.UpdateSpace("Blocked", null, null)));

    assertThat(pairing.reactivate(sam.getId()).status()).isEqualTo("connected");
    var pending = pairing.requestUnpair(alex.getId());
    assertThat(pending.unpairPending()).isTrue();
    assertThat(pending.unpairRequestedByMe()).isTrue();
    assertThat(pairing.getLifecycle(sam.getId()).unpairRequestedByMe()).isFalse();

    var cancelled = pairing.cancelUnpair(sam.getId());
    assertThat(cancelled.unpairPending()).isFalse();
    assertThat(pairing.reactivate(alex.getId()).status()).isEqualTo("connected");
  }

  @Test
  void inviteOwnershipAndConfirmedPartnerCannotBeForged() {
    User founder = user("founder@example.test", "Founder");
    User redeemer = user("redeemer@example.test", "Redeemer");
    User stranger = user("stranger@example.test", "Stranger");
    String code = pairing.createInvite(founder.getId()).code();

    pairing.cancelInvite(stranger.getId(), code);
    pairing.redeemCode(redeemer.getId(), code);

    assertCode(ErrorCode.CODE_INVALID,
        () -> pairing.confirmPartner(redeemer.getId(), stranger.getId()));
    assertThat(members.findByUserId(redeemer.getId())).isEmpty();
    assertThat(pairing.getSpace(founder.getId()).status()).isEqualTo("pending");
  }

  @Test
  void cancellingAnUnusedInviteReturnsFounderToUnpairedState() {
    User founder = user("founder@example.test", "Founder");
    String code = pairing.createInvite(founder.getId()).code();

    pairing.cancelInvite(founder.getId(), code);

    assertThat(pairing.getSpace(founder.getId()).status()).isEqualTo("none");
    assertThat(members.findByUserId(founder.getId())).isEmpty();
    assertThat(couples.count()).isZero();
  }

  private boolean redeemAfter(CountDownLatch start, UUID userId, String code) throws Exception {
    start.await();
    try {
      pairing.redeemCode(userId, code);
      return true;
    } catch (AppException exception) {
      assertThat(exception.code()).isEqualTo(ErrorCode.CODE_INVALID);
      return false;
    }
  }

  private User user(String email, String name) {
    User user = new User();
    user.setEmail(email);
    user.setEmailVerified(true);
    user.setDisplayName(name);
    return users.saveAndFlush(user);
  }

  private static void assertCode(ErrorCode code, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(AppException.class,
            exception -> assertThat(exception.code()).isEqualTo(code));
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