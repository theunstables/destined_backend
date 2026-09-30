package com.loveos.api.pairing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import com.loveos.api.pairing.dto.PairingResponses;
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

class PairingControllerContractTest {

  private PairingService pairing;
  private MockMvc mvc;
  private UUID userId;

  @BeforeEach
  void setUp() {
    pairing = org.mockito.Mockito.mock(PairingService.class);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc = MockMvcBuilders.standaloneSetup(new PairingController(pairing))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter())
        .build();

    userId = UUID.randomUUID();
    var principal = new AuthenticatedUser(userId, "sam@example.test", true);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null));
  }

  @Test
  void inviteAndSpaceResponsesMatchFrontendContract() throws Exception {
    when(pairing.createInvite(userId)).thenReturn(
        new PairingResponses.InviteDto("ABC234", "2026-09-19T00:00:00Z"));
    when(pairing.getSpace(userId)).thenReturn(new PairingResponses.SpaceDto(
        "couple-id", null, null, "dawn", "inviting", null));

    mvc.perform(post("/v1/pairing/invites"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.code").value("ABC234"))
        .andExpect(jsonPath("$.data.expiresAt").isString());
    mvc.perform(get("/v1/pairing/space"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("inviting"))
        .andExpect(jsonPath("$.data.coverStyle").value("dawn"));
  }

  @Test
  void profileValidationReturnsFieldIssues() throws Exception {
    mvc.perform(put("/v1/pairing/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"\",\"birthday\":\"tomorrow\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.issues").isArray());
  }

  @Test
  void confirmReturnsPartnerEnvelope() throws Exception {
    UUID partnerId = UUID.randomUUID();
    when(pairing.confirmPartner(userId, partnerId)).thenReturn(
        new PairingResponses.PartnerDto(partnerId.toString(), "Alex", null));

    mvc.perform(post("/v1/pairing/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"partnerId\":\"" + partnerId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(partnerId.toString()))
        .andExpect(jsonPath("$.data.name").value("Alex"));
  }
}