package com.loveos.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.account.AccountController;
import com.loveos.api.account.AccountService;
import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import com.loveos.api.notifications.NotificationController;
import com.loveos.api.notifications.NotificationDtos;
import com.loveos.api.notifications.NotificationService;
import com.loveos.api.timeline.TimelineController;
import com.loveos.api.timeline.TimelineService;
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

class ProductCompletionControllerContractTest {

  private NotificationService notifications;
  private AccountService accounts;
  private TimelineService timeline;
  private MockMvc mvc;
  private UUID userId;

  @BeforeEach
  void setUp() {
    notifications = org.mockito.Mockito.mock(NotificationService.class);
    accounts = org.mockito.Mockito.mock(AccountService.class);
    timeline = org.mockito.Mockito.mock(TimelineService.class);
    mvc = MockMvcBuilders.standaloneSetup(
        new NotificationController(notifications), new AccountController(accounts),
        new TimelineController(timeline))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter()).build();
    userId = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, "alex@example.test", true), null));
  }

  @Test
  void timelineReturnsCursorInEnvelopeMetadata() throws Exception {
    when(timeline.list(userId, null, 2)).thenReturn(new TimelineService.Page(
        List.of(new TimelineService.Item(
            "item", "memory", "2025-01-01", "Rome", null, null)), "next"));

    mvc.perform(get("/v1/timeline").param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].type").value("memory"))
        .andExpect(jsonPath("$.meta.nextCursor").value("next"));
  }

  @Test
  void preferencesValidateAndReturnTypedShape() throws Exception {
    when(notifications.updatePreferences(eq(userId), any()))
        .thenReturn(new NotificationDtos.Preferences(
            true, true, false, true, "22:00", "07:00", "UTC"));

    mvc.perform(put("/v1/notifications/preferences")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"enabled":true,"messages":true,"occasions":false,"memories":true,
                 "quietStart":"22:00","quietEnd":"07:00","timezone":"UTC"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.occasions").value(false))
        .andExpect(jsonPath("$.data.timezone").value("UTC"));
  }

  @Test
  void accountDeletionUsesGracePeriodResponse() throws Exception {
    when(accounts.requestDeletion(userId)).thenReturn(new AccountService.DeletionStatus(
        true, "2025-01-01T00:00:00Z", "2025-01-31T00:00:00Z", null, null));

    mvc.perform(post("/v1/account/deletion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pending").value(true))
        .andExpect(jsonPath("$.data.executeAfter").value("2025-01-31T00:00:00Z"));
  }
}
