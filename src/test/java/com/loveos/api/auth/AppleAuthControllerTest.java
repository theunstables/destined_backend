package com.loveos.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.core.GlobalExceptionHandler;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AppleAuthControllerTest {
  private AppleAuthService apple;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    apple = mock(AppleAuthService.class);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    SecurityContextHolder.clearContext();
    mvc = MockMvcBuilders.standaloneSetup(new AppleAuthController(apple, new TokenHasher()))
        .setValidator(validator).setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
  }

  @AfterEach
  void clearContext() { SecurityContextHolder.clearContext(); }

  @Test
  void configExposesOnlyAvailability() throws Exception {
    mvc.perform(get("/v1/auth/apple/config")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false))
        .andExpect(jsonPath("$.data.privateKey").doesNotExist());
  }

  @Test
  void linkingRequiresAuthenticatedVerifiedPrincipal() throws Exception {
    String body = "{\"platform\":\"web\",\"link\":true}";
    mvc.perform(post("/v1/auth/apple/start").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(UUID.randomUUID(), "qa@example.test", false), null));
    mvc.perform(post("/v1/auth/apple/start").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void linkingBindsOnlyTheAuthenticatedAccount() throws Exception {
    UUID userId = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, "qa@example.test", true), null));
    when(apple.start("web", userId)).thenReturn(new AppleAuthService.Start(
        "https://appleid.apple.com/auth/authorize", "state", "exchange-secret", "https://app.example.test/apple-callback"));
    mvc.perform(post("/v1/auth/apple/start").contentType(MediaType.APPLICATION_JSON)
        .content("{\"platform\":\"web\",\"link\":true}"))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.state").value("state"));
  }

  @Test
  void formPostCallbackRedirectsWith303WithoutCredentials() throws Exception {
    when(apple.callback(eq("state"), eq("code"), any())).thenReturn(URI.create("https://app.example.test/apple-callback?ticket=single-use"));
    mvc.perform(post("/v1/auth/apple/callback").contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .param("state", "state").param("code", "code").param("id_token", "untrusted-front-channel-token"))
        .andExpect(status().isSeeOther()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string("Location", "https://app.example.test/apple-callback?ticket=single-use"));
  }

  @Test
  void exchangeRejectsMissingInitiatorSecret() throws Exception {
    mvc.perform(post("/v1/auth/apple/exchange").contentType(MediaType.APPLICATION_JSON)
        .content("{\"ticket\":\"ticket\"}"))
        .andExpect(status().isUnprocessableEntity());
  }
}