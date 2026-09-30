package com.loveos.api.repairsignal;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RepairSignalControllerContractTest {

  private RepairSignalService service;
  private MockMvc mvc;
  private UUID userId;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(RepairSignalService.class);
    mvc = MockMvcBuilders.standaloneSetup(new RepairSignalController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter()).build();
    userId = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, "alex@example.test", true), null));
  }

  @Test
  void currentMayReturnNullWithoutInventingANegativeState() throws Exception {
    when(service.current(userId)).thenReturn(null);
    mvc.perform(get("/v1/repair-signal"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void sendReturnsMinimalPressureSafeShape() throws Exception {
    when(service.send(userId)).thenReturn(new RepairSignalDtos.Signal(
        "signal-id", "open", true, false, "2026-09-18T12:00:00Z", null,
        "2026-09-19T12:00:00Z"));

    mvc.perform(post("/v1/repair-signal"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("open"))
        .andExpect(jsonPath("$.data.sentByMe").value(true))
        .andExpect(jsonPath("$.data.mutual").value(false))
        .andExpect(jsonPath("$.data.readAt").doesNotExist())
        .andExpect(jsonPath("$.data.coupleId").doesNotExist());
  }

  @Test
  void senderCanCancelIdempotently() throws Exception {
    doNothing().when(service).cancel(userId);
    mvc.perform(delete("/v1/repair-signal"))
        .andExpect(status().isNoContent());
  }
}
