package com.loveos.api.dailyquestions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DailyQuestionControllerContractTest {

  private DailyQuestionService service;
  private MockMvc mvc;
  private UUID userId;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(DailyQuestionService.class);
    mvc = MockMvcBuilders.standaloneSetup(new DailyQuestionController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter()).build();
    userId = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, "alex@example.test", true), null));
  }

  @Test
  void returnsMaskedPartnerAnswerBeforeCompletion() throws Exception {
    when(service.today(userId)).thenReturn(question(
        "waiting", true, "My answer", null, null));

    mvc.perform(get("/v1/daily-question"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("waiting"))
        .andExpect(jsonPath("$.data.partnerHasAnswered").value(true))
        .andExpect(jsonPath("$.data.myAnswer").value("My answer"))
        .andExpect(jsonPath("$.data.partnerAnswer").doesNotExist());
  }

  @Test
  void validatesAndReturnsCompletedAnswer() throws Exception {
    when(service.answer(eq(userId), any())).thenReturn(question(
        "complete", true, "Mine", "Theirs", "memory-id"));

    mvc.perform(put("/v1/daily-question/answer")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"answer\":\"Mine\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.partnerAnswer").value("Theirs"))
        .andExpect(jsonPath("$.data.memoryId").value("memory-id"));

    mvc.perform(put("/v1/daily-question/answer")
            .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  private static DailyQuestionDtos.DailyQuestion question(
      String status, boolean partnerHasAnswered, String mine, String partner, String memoryId) {
    return new DailyQuestionDtos.DailyQuestion(
        "day-id", "2026-09-18", "What made you smile?", status,
        partnerHasAnswered, mine, partner, "2026-09-18T12:00:00Z",
        partner == null ? null : "2026-09-18T12:01:00Z", memoryId);
  }
}
