package com.loveos.api.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.billing.BillingController;
import com.loveos.api.compatibility.CompatibilityController;
import com.loveos.api.content.AdaptiveContentController;
import com.loveos.api.core.DateUtils;
import com.loveos.api.pairing.CoupleAccess;
import com.loveos.api.story.StoryAccess;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderIndependentControllerTest {
  @Mock CoupleAccess couples;
  @Mock StoryAccess stories;

  private final UUID viewerId = UUID.randomUUID();
  private final UUID partnerId = UUID.randomUUID();
  private final UUID coupleId = UUID.randomUUID();
  private final AuthenticatedUser principal =
      new AuthenticatedUser(viewerId, "viewer@example.test", true);

  @Test
  void billingReturnsOnlyAvailableEntitlementsWithoutSimulatingCheckout() {
    var response = new BillingController().get(principal);

    assertThat(response.data().currentPlan()).isEqualTo("free");
    assertThat(response.data().providerConfigured()).isFalse();
    assertThat(response.data().plans()).anySatisfy(plan -> {
      assertThat(plan.key()).isEqualTo("free");
      assertThat(plan.current()).isTrue();
      assertThat(plan.available()).isTrue();
    });
    assertThat(response.data().plans()).anySatisfy(plan -> {
      assertThat(plan.key()).isEqualTo("premium");
      assertThat(plan.current()).isFalse();
      assertThat(plan.available()).isFalse();
    });
    assertThat(response.data().invoices()).isEmpty();
  }

  @Test
  void compatibilityIsViewerFirstAndUsesStoredBirthdaysAndMetDate() {
    when(couples.members(viewerId)).thenReturn(List.of(
        new CoupleAccess.Member(partnerId, CoupleAccess.Role.MEMBER, "Partner Name", "Love",
            LocalDate.of(1992, 7, 8)),
        new CoupleAccess.Member(viewerId, CoupleAccess.Role.FOUNDER, "Viewer Name", null,
            LocalDate.of(1991, 2, 3))));
    when(stories.metDate(viewerId)).thenReturn(DateUtils.utcToday().minusDays(10));

    var response = new CompatibilityController(couples, stories).get(principal);

    assertThat(response.data().people()).extracting(CompatibilityController.Person::id)
        .containsExactly(viewerId.toString(), partnerId.toString());
    assertThat(response.data().people().get(0).name()).isEqualTo("Viewer Name");
    assertThat(response.data().people().get(0).birthday()).isEqualTo("1991-02-03");
    assertThat(response.data().people().get(1).name()).isEqualTo("Love");
    assertThat(response.data().daysTogether()).isEqualTo(10L);
  }

  @Test
  void adaptiveSuggestionsAreStableForTheSameCoupleAndUtcDate() {
    when(couples.requireMembership(viewerId)).thenReturn(new CoupleAccess.Membership(
        coupleId, CoupleAccess.Role.FOUNDER, true));
    var controller = new AdaptiveContentController(couples);

    var first = controller.suggestions(principal).data();
    var second = controller.suggestions(principal).data();

    assertThat(first).isEqualTo(second);
    assertThat(first.generatedFor()).isEqualTo(DateUtils.utcToday().toString());
    assertThat(first.items()).hasSize(2).doesNotHaveDuplicates();
    assertThat(first.items()).allSatisfy(item ->
        assertThat(item.action()).isIn("memory", "calendar", "chat"));
  }
}
