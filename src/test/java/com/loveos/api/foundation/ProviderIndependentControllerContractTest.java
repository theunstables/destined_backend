package com.loveos.api.foundation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.billing.BillingController;
import com.loveos.api.compatibility.CompatibilityController;
import com.loveos.api.content.AdaptiveContentController;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import com.loveos.api.feedback.FeedbackController;
import com.loveos.api.feedback.FeedbackService;
import com.loveos.api.media.StorageUsageController;
import com.loveos.api.media.StorageUsageService;
import com.loveos.api.pairing.CoupleAccess;
import com.loveos.api.story.StoryAccess;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProviderIndependentControllerContractTest {
  private FeedbackService feedback;
  private StorageUsageService storage;
  private CoupleAccess couples;
  private StoryAccess stories;
  private MockMvc mvc;
  private UUID userId;

  @BeforeEach
  void setUp() {
    feedback = org.mockito.Mockito.mock(FeedbackService.class);
    storage = org.mockito.Mockito.mock(StorageUsageService.class);
    couples = org.mockito.Mockito.mock(CoupleAccess.class);
    stories = org.mockito.Mockito.mock(StoryAccess.class);
    mvc = MockMvcBuilders.standaloneSetup(
      new FeedbackController(feedback), new StorageUsageController(storage),
      new BillingController(), new CompatibilityController(couples, stories),
      new AdaptiveContentController(couples))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter())
        .build();
    userId = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, "alex@example.test", true), null));
  }

  @Test
  void storageUsageReturnsTheRequiredSuccessEnvelope() throws Exception {
    when(storage.get(userId)).thenReturn(new StorageUsageService.Usage(700, 125, 500, 75));

    mvc.perform(get("/v1/account/storage"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalBytes").value(700))
        .andExpect(jsonPath("$.data.photoBytes").value(125))
        .andExpect(jsonPath("$.data.videoBytes").value(500))
        .andExpect(jsonPath("$.data.audioBytes").value(75));
  }

  @Test
  void feedbackReturnsCreatedReceiptInTheRequiredSuccessEnvelope() throws Exception {
    UUID receiptId = UUID.randomUUID();
    when(feedback.submit(eq(userId), any())).thenReturn(new FeedbackService.Receipt(
        receiptId, "NEW", Instant.parse("2026-10-18T12:00:00Z")));

    mvc.perform(post("/v1/feedback")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"topic":"idea","message":"A complete improvement suggestion."}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(receiptId.toString()))
        .andExpect(jsonPath("$.data.status").value("NEW"))
        .andExpect(jsonPath("$.data.createdAt").value("2026-10-18T12:00:00Z"));
  }

  @Test
  void billingExposesProviderAvailabilityWithoutAFalseCheckoutContract() throws Exception {
    mvc.perform(get("/v1/account/billing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.currentPlan").value("free"))
        .andExpect(jsonPath("$.data.providerConfigured").value(false))
        .andExpect(jsonPath("$.data.plans[0].available").value(true))
        .andExpect(jsonPath("$.data.plans[1].available").value(false))
        .andExpect(jsonPath("$.data.invoices").isArray());
  }

  @Test
  void compatibilityUsesAuthenticatedViewerAndReturnsProfileProjection() throws Exception {
    UUID partnerId = UUID.randomUUID();
    when(couples.members(userId)).thenReturn(List.of(
        new CoupleAccess.Member(userId, CoupleAccess.Role.FOUNDER, "Alex", null,
            LocalDate.of(1990, 1, 2)),
        new CoupleAccess.Member(partnerId, CoupleAccess.Role.MEMBER, "Sam", "Love",
            LocalDate.of(1991, 3, 4))));
    when(stories.metDate(userId)).thenReturn(null);

    mvc.perform(get("/v1/compatibility"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.people[0].id").value(userId.toString()))
        .andExpect(jsonPath("$.data.people[0].birthday").value("1990-01-02"))
        .andExpect(jsonPath("$.data.people[1].name").value("Love"))
        .andExpect(jsonPath("$.data.daysTogether").doesNotExist());
  }

  @Test
  void adaptiveContentReturnsCuratedSuggestionsInTheSuccessEnvelope() throws Exception {
    when(couples.requireMembership(userId)).thenReturn(new CoupleAccess.Membership(
        UUID.randomUUID(), CoupleAccess.Role.FOUNDER, true));

    mvc.perform(get("/v1/content/suggestions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.generatedFor").isString())
        .andExpect(jsonPath("$.data.items.length()").value(2))
        .andExpect(jsonPath("$.data.items[0].action").isString());
  }
}
