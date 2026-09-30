package com.loveos.api.home;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import com.loveos.api.home.dto.HomeResponses;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HomeControllerContractTest {
  private HomeService service;
  private MockMvc mvc;
  private UUID userId;

  @BeforeEach
  void setUp() {
    service = org.mockito.Mockito.mock(HomeService.class);
    mvc = MockMvcBuilders.standaloneSetup(new HomeController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter()).build();
    userId = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, "alex@example.test", true), null));
  }

  @Test
  void returnsSingleDashboardEnvelope() throws Exception {
    when(service.getHome(userId)).thenReturn(new HomeResponses.HomeDto(
        "Al & Sam", "Al", new HomeResponses.Space("Our Place", "Us", "night"), 1395L,
        List.of(new HomeResponses.Stat("together", "heart", "1,395", "Together", "days")),
        List.of(), List.of(new HomeResponses.RecentMemory(
            "id", "Rome", "2024-06-12", "https://example.test/rome.jpg", "Rome")),
        new HomeResponses.Pulse("Relationship pulse", "1,395", "days", "since we first met.")));

    mvc.perform(get("/v1/home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.coupleName").value("Al & Sam"))
        .andExpect(jsonPath("$.data.space.coverStyle").value("night"))
        .andExpect(jsonPath("$.data.stats[0].value").value("1,395"))
        .andExpect(jsonPath("$.data.recentMemories[0].photoUri")
            .value("https://example.test/rome.jpg"));
  }
}