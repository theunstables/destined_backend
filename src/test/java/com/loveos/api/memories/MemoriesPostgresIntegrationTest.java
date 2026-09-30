package com.loveos.api.memories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loveos.api.account.AccountService;
import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.memories.dto.MemoryRequests;
import com.loveos.api.pairing.domain.*;
import com.loveos.api.pairing.repo.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
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
class MemoriesPostgresIntegrationTest {
  @Autowired private MemoriesService service;
  @Autowired private AccountService accounts;
  @Autowired private UserRepository users;
  @Autowired private CoupleRepository couples;
  @Autowired private CoupleMemberRepository members;
  private User founder;
  private User partner;
  private Couple couple;

  @BeforeEach
  void setUp() {
    founder = user("memory-" + UUID.randomUUID() + "@example.test", "Founder", "Alex");
    partner = user("memory-partner-" + UUID.randomUUID() + "@example.test", "Partner", "Sam");
    couple = new Couple();
    couple.setStatus(CoupleStatus.CONNECTED);
    couples.saveAndFlush(couple);
    member(founder, couple, CoupleRole.FOUNDER);
    member(partner, couple, CoupleRole.MEMBER);
  }

  @Test
  void createStoresOrderedDeduplicatedPhotosAndCaseInsensitiveTags() {
    var result = service.create(founder.getId(), create("Rome", "2024-06-12",
        "https://example.test/cover.jpg",
        List.of("https://example.test/cover.jpg", "https://example.test/two.jpg"),
        List.of(" Trips ", "trips", "Us"), null));

    assertThat(result.photos()).containsExactly(
        "https://example.test/cover.jpg", "https://example.test/two.jpg");
    assertThat(result.tags()).containsExactly("Trips", "Us");
    assertThat(result.addedBy()).isEqualTo("Alex");
  }

  @Test
  void listPaginatesAfterFilteringAndSupportsOnThisDay() {
    String today = LocalDate.now().withYear(2020).toString();
    service.create(founder.getId(), create("One", today, null, null, List.of("Day"), null));
    service.create(founder.getId(), create("Two", today, null, null, List.of("Day"), null));
    service.create(founder.getId(), create("Other", "2020-01-02", null, null, List.of(), null));

    var first = service.list(founder.getId(), null, 1, "day", null, null, today.substring(5));
    var second = service.list(founder.getId(), first.nextCursor(), 1,
        "day", null, null, today.substring(5));

    assertThat(first.items()).hasSize(1);
    assertThat(first.nextCursor()).isNotNull();
    assertThat(second.items()).hasSize(1);
    assertThat(second.items().getFirst().id()).isNotEqualTo(first.items().getFirst().id());
  }

  @Test
  void favoriteSearchAndSoftDeleteRoundTrip() {
    var created = service.create(founder.getId(), create("Rome", "2024-06-12", null, null,
        List.of("Trips"), null));
    UUID id = UUID.fromString(created.id());

    assertThat(service.search(founder.getId(), "trip", 50)).hasSize(1);
    assertThat(service.toggleFavorite(founder.getId(), id).favorite()).isTrue();
    service.delete(founder.getId(), id);
    assertThat(service.list(founder.getId(), null, 50, null, null, null, null).items()).isEmpty();
  }

  @Test
  void connectedCoupleGetsStarterAlbumsAndCustomMembershipIsIdempotent() {
    var starters = service.listAlbums(founder.getId());
    assertThat(starters).extracting(item -> item.key())
        .containsExactly("us", "trips", "dates", "birthdays", "littlethings");

    var memory = service.create(founder.getId(), create("Trip", "2024-06-12", null, null,
        List.of(), null));
    UUID albumId = UUID.fromString(starters.get(1).id());
    UUID memoryId = UUID.fromString(memory.id());
    service.addToAlbum(founder.getId(), albumId, memoryId);
    service.addToAlbum(founder.getId(), albumId, memoryId);
    assertThat(service.getAlbum(founder.getId(), "trips").count()).isEqualTo(1);
  }

  @Test
  void memoryIdCannotCrossCoupleBoundary() {
    var memory = service.create(founder.getId(), create("Private", "2024-06-12", null, null,
        List.of(), null));
    User stranger = user("stranger-" + UUID.randomUUID() + "@example.test", "Stranger", null);
    Couple other = new Couple();
    couples.saveAndFlush(other);
    member(stranger, other, CoupleRole.FOUNDER);

    assertThatThrownBy(() -> service.get(stranger.getId(), UUID.fromString(memory.id())))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  void reciprocalNotesRemainMaskedUntilBothMembersSubmit() {
    var memory = service.create(founder.getId(), create(
        "Rome", "2024-06-12", null, null, List.of(), null));
    UUID id = UUID.fromString(memory.id());

    var first = service.upsertPrivateNote(founder.getId(), id, "What I remember");
    assertThat(first.myPrivateNote()).isEqualTo("What I remember");
    assertThat(first.partnerPrivateNote()).isNull();
    assertThat(first.reciprocalNotesRevealed()).isFalse();
    assertThat(service.get(partner.getId(), id).partnerPrivateNote()).isNull();

    var completed = service.upsertPrivateNote(partner.getId(), id, "What you made me feel");
    assertThat(completed.partnerPrivateNote()).isEqualTo("What I remember");
    assertThat(completed.reciprocalNotesRevealed()).isTrue();
    assertThat(service.get(founder.getId(), id).partnerPrivateNote())
        .isEqualTo("What you made me feel");
  }

  @Test
  void noteReplacementIsIdempotentAndWithdrawalRemasksPartnerNote() {
    var memory = service.create(founder.getId(), create(
        "Rome", "2024-06-12", null, null, List.of(), null));
    UUID id = UUID.fromString(memory.id());
    service.upsertPrivateNote(founder.getId(), id, "Mine");
    service.upsertPrivateNote(founder.getId(), id, "Mine");
    service.upsertPrivateNote(partner.getId(), id, "Theirs");

    var withdrawn = service.withdrawPrivateNote(founder.getId(), id);
    assertThat(withdrawn.myPrivateNote()).isNull();
    assertThat(withdrawn.partnerPrivateNote()).isNull();
    assertThat(withdrawn.reciprocalNotesRevealed()).isFalse();
    assertThat(service.withdrawPrivateNote(founder.getId(), id).myPrivateNote()).isNull();
  }

  @Test
  void simultaneousNotesConvergeToReciprocalReveal() throws Exception {
    var memory = service.create(founder.getId(), create(
        "Rome", "2024-06-12", null, null, List.of(), null));
    UUID id = UUID.fromString(memory.id());
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var one = executor.submit(() -> {
        start.await();
        return service.upsertPrivateNote(founder.getId(), id, "Mine");
      });
      var two = executor.submit(() -> {
        start.await();
        return service.upsertPrivateNote(partner.getId(), id, "Theirs");
      });
      start.countDown();
      one.get();
      two.get();
    }
    assertThat(service.get(founder.getId(), id).reciprocalNotesRevealed()).isTrue();
    assertThat(service.get(partner.getId(), id).reciprocalNotesRevealed()).isTrue();
  }

  @Test
  void privateNoteCannotCrossCoupleBoundary() {
    var memory = service.create(founder.getId(), create(
        "Private", "2024-06-12", null, null, List.of(), null));
    User stranger = user("note-stranger-" + UUID.randomUUID() + "@example.test", "Stranger", null);
    Couple other = new Couple();
    couples.saveAndFlush(other);
    member(stranger, other, CoupleRole.FOUNDER);

    assertThatThrownBy(() -> service.upsertPrivateNote(
        stranger.getId(), UUID.fromString(memory.id()), "No access"))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  void accountExportIncludesOnlyTheRequestingMembersPrivateNotes() {
    var memory = service.create(founder.getId(), create(
        "Private", "2024-06-12", null, null, List.of(), null));
    UUID id = UUID.fromString(memory.id());
    service.upsertPrivateNote(founder.getId(), id, "Mine only");
    service.upsertPrivateNote(partner.getId(), id, "Theirs only");

    Object section = accounts.export(founder.getId()).data().get("my_memory_private_notes");
    assertThat(section).asString().contains("Mine only").doesNotContain("Theirs only");
  }

  private MemoryRequests.CreateMemory create(
      String title, String date, String cover, List<String> photos, List<String> tags,
      List<String> albumIds) {
    return new MemoryRequests.CreateMemory(title, date, null, "Rome", "Our note", cover,
        photos, tags, false, albumIds);
  }

  private User user(String email, String name, String nickname) {
    User user = new User();
    user.setEmail(email); user.setEmailVerified(true); user.setDisplayName(name);
    user.setNickname(nickname); return users.saveAndFlush(user);
  }

  private void member(User user, Couple target, CoupleRole role) {
    CoupleMember membership = new CoupleMember();
    membership.setCoupleId(target.getId()); membership.setUserId(user.getId());
    membership.setRole(role); members.saveAndFlush(membership);
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