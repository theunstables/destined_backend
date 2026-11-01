package com.loveos.api.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.loveos.api.core.AppException;
import com.loveos.api.pairing.CoupleAccess;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class PlansServiceTest {
  private final ObjectMapper json = new ObjectMapper();
  private final UUID user = UUID.randomUUID();
  private final UUID couple = UUID.randomUUID();
  private PlansRepository repository;
  private PlansService service;

  @BeforeEach
  void setUp() {
    CoupleAccess access = mock(CoupleAccess.class);
    repository = mock(PlansRepository.class);
    when(access.requireMembership(user)).thenReturn(new CoupleAccess.Membership(couple, CoupleAccess.Role.FOUNDER, true));
    when(access.requireMembershipForUpdate(user)).thenReturn(new CoupleAccess.Membership(couple, CoupleAccess.Role.FOUNDER, true));
    when(repository.findById(couple)).thenReturn(Optional.empty());
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
      PlansDocument document = invocation.getArgument(0);
      document.setRevision(document.getRevision() == null ? 0 : document.getRevision() + 1);
      when(repository.findById(couple)).thenReturn(Optional.of(document));
      return document;
    });
    service = new PlansService(access, repository, json);
  }

  @Test
  void savesBingoAndRejectsStaleRevision() {
    ObjectNode saved = service.replace(user, empty());
    assertThat(saved.path("revision").asLong()).isZero();
    assertThat(service.get(user).path("yearBoard").path("tiles").size()).isZero();
    assertThatThrownBy(() -> service.replace(user, empty())).isInstanceOf(AppException.class);
  }

  @Test
  void doesNotPersistOrReturnUnknownClientStoreFields() {
    ObjectNode input = empty();
    input.putNull("reset");
    input.put("saving", true);
    ObjectNode saved = service.replace(user, input);
    assertThat(saved.has("reset")).isFalse();
    assertThat(saved.has("saving")).isFalse();
    assertThat(service.get(user).has("reset")).isFalse();
  }

  @Test
  void sealedPayloadIsNeverReturnedAndEarlyOpeningIsRejected() {
    ObjectNode input = empty();
    ObjectNode capsule = input.withArray("capsules").addObject();
    capsule.put("id", "capsule-1").put("title", "For later")
        .put("opensAt", LocalDate.now(ZoneOffset.UTC).plusDays(1).toString());
    capsule.putObject("payload").put("letter", "private content");
    ObjectNode saved = service.replace(user, input);
    assertThat(saved.path("capsules").get(0).has("payload")).isFalse();
    ObjectNode changed = (ObjectNode) saved.path("capsules").get(0);
    changed.put("opensAt", "2000-01-01").put("opened", true);
    assertThatThrownBy(() -> service.replace(user, saved)).isInstanceOf(AppException.class);
    assertThat(service.get(user).toString()).doesNotContain("private content");
  }

  @Test
  void omittedPayloadSurvivesARegularPlansUpdate() {
    ObjectNode input = empty();
    ObjectNode capsule = input.withArray("capsules").addObject();
    capsule.put("id", "capsule-1").put("title", "For later")
        .put("opensAt", LocalDate.now(ZoneOffset.UTC).plusDays(1).toString());
    capsule.putObject("payload").put("letter", "private content");
    ObjectNode saved = service.replace(user, input);
    service.replace(user, saved);
    assertThat(repository.findById(couple).orElseThrow().getPayload()).contains("private content");
  }

  private ObjectNode empty() {
    ObjectNode value = json.createObjectNode();
    value.putObject("yearBoard").put("year", 2026).putArray("tiles");
    value.putArray("capsules");
    value.putArray("letters");
    return value;
  }

  @Test
  void dueCapsuleReturnsItsPreservedContentOnlyAfterOpening() {
    ObjectNode input = empty();
    ObjectNode capsule = input.withArray("capsules").addObject();
    capsule.put("id", "capsule-1").put("title", "For later")
        .put("opensAt", LocalDate.now(ZoneOffset.UTC).plusDays(1).toString());
    capsule.putObject("payload").put("letter", "private content");
    service.replace(user, input);
    PlansDocument document = repository.findById(couple).orElseThrow();
    ObjectNode stored = (ObjectNode) json.readTree(document.getPayload());
    ((ObjectNode) stored.path("capsules").get(0)).put("opensAt", "2000-01-01");
    document.setPayload(json.writeValueAsString(stored));
    ObjectNode saved = service.get(user);
    assertThat(saved.path("capsules").get(0).has("payload")).isFalse();
    ((ObjectNode) saved.path("capsules").get(0)).put("opened", true);
    ObjectNode opened = service.replace(user, saved);
    assertThat(opened.path("capsules").get(0).path("payload").path("letter").asText()).isEqualTo("private content");
  }

  @Test
  void rejectsInvalidBingoCards() {
    ObjectNode input = empty();
    input.withObject("yearBoard").withArray("tiles").addObject()
        .put("id", "card-1").put("title", "").put("state", "todo");
    assertThatThrownBy(() -> service.replace(user, input)).isInstanceOf(AppException.class);
  }
}