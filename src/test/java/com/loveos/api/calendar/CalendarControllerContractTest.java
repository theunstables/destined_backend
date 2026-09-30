package com.loveos.api.calendar;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.calendar.dto.CalendarResponses;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class CalendarControllerContractTest {

  private CalendarService service;
  private MockMvc mvc;
  private UUID userId;
  private UUID eventId;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(CalendarService.class);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc = MockMvcBuilders.standaloneSetup(new CalendarController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter())
        .build();
    userId = UUID.randomUUID();
    eventId = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, "alex@example.test", true), null));
  }

  @Test
  void createAcceptsFrontendShapeAndReturnsCreatedEvent() throws Exception {
    when(service.create(eq(userId), any())).thenReturn(event());

    mvc.perform(post("/v1/calendar")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"Date night","date":"2026-10-01","startsAt":"2026-10-01T18:00:00Z",
                 "kind":"dateNight","repeatsAnnually":false,"reminderMinutesBefore":60}
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(eventId.toString()))
        .andExpect(jsonPath("$.data.kind").value("dateNight"));
  }

  @Test
  void invalidNestedInputReturnsValidationEnvelope() throws Exception {
    mvc.perform(post("/v1/calendar")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"\",\"date\":\"2026\",\"kind\":\"weekly\"}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void upcomingUsesStableComingUpShape() throws Exception {
    when(service.upcoming(userId, 365, 10)).thenReturn(List.of(
        new CalendarResponses.ComingUpDto("anniversary", "Our Anniversary", "Oct 1", 13,
            "2020-10-01", "anniversary", "keyDate")));

    mvc.perform(get("/v1/calendar/upcoming"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].days").value(13))
        .andExpect(jsonPath("$.data[0].source").value("keyDate"));
  }

  @Test
  void deleteReturnsNoContent() throws Exception {
    mvc.perform(delete("/v1/calendar/{id}", eventId))
        .andExpect(status().isNoContent());
  }

  private CalendarResponses.EventDto event() {
    return new CalendarResponses.EventDto(eventId.toString(), "Date night", "2026-10-01",
        "2026-10-01T18:00:00Z", null, null, null, "dateNight", false, 60,
        "2026-09-18T00:00:00Z");
  }
}