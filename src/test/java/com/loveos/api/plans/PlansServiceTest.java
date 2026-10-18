package com.loveos.api.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.loveos.api.pairing.CoupleAccess;
import com.loveos.api.core.AppException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PlansServiceTest {
  @Mock CouplePlanStateRepository repository;
  @Mock CoupleAccess couples;
  @Captor ArgumentCaptor<CouplePlanState> row;

  private final UUID me = UUID.randomUUID();
  private final UUID partner = UUID.randomUUID();
  private final UUID coupleId = UUID.randomUUID();
  private ObjectMapper mapper;
  private PlansService service;

  @BeforeEach
  void setUp() {
    mapper = JsonMapper.builder().build();
    service = new PlansService(repository, couples, mapper);
    when(couples.requireMembershipForUpdate(me)).thenReturn(
        new CoupleAccess.Membership(coupleId, CoupleAccess.Role.FOUNDER, true));
    when(couples.members(me)).thenReturn(List.of(
        new CoupleAccess.Member(me, CoupleAccess.Role.FOUNDER, "Me", null),
        new CoupleAccess.Member(partner, CoupleAccess.Role.MEMBER, "Partner", null)));
    when(repository.findById(coupleId)).thenReturn(Optional.empty());
  }

  @Test
  void storesCanonicalMemberIdsButReturnsViewerRelativeIds() throws Exception {
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.replace(me, mapper.readTree(state("me", "partner", "opened", "hello")));

    org.mockito.Mockito.verify(repository).saveAndFlush(row.capture());
    assertThat(row.getValue().getJson()).contains(me.toString()).contains(partner.toString());
    assertThat(result.at("/letters/0/from").asText()).isEqualTo("me");
    assertThat(result.at("/letters/0/to").asText()).isEqualTo("partner");
  }

  @Test
  void neverReturnsBodyOfSealedLetter() throws Exception {
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.replace(me, mapper.readTree(state("me", "partner", "sealed", "secret")));

    assertThat(result.at("/letters/0/body").isNull()).isTrue();
    org.mockito.Mockito.verify(repository).saveAndFlush(row.capture());
    assertThat(row.getValue().getJson()).contains("secret");
  }

  @Test
  void rejectsOpeningCapsuleBeforeItsDueDate() throws Exception {
    String value = state("me", "partner", "opened", "hello")
        .replace("\"capsules\":[]", "\"capsules\":[{\"id\":\"cap-1\",\"opensAt\":\"2099-01-01\",\"opened\":true}]");

    assertThatThrownBy(() -> service.replace(me, mapper.readTree(value)))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.getMessage()).contains("cannot be opened yet"));
  }

  private static String state(String from, String to, String status, String body) {
    return """
        {"trip":{},"yearBoard":{},"promises":[],"lifetimeTarget":50,"watchRoom":null,
         "capsules":[],"sharingWindow":null,"minutesRemaining":0,
         "letters":[{"id":"letter-1","from":"%s","to":"%s","state":"%s",
         "opensAt":"2099-01-01","body":"%s"}]}
        """.formatted(from, to, status, body);
  }
}
