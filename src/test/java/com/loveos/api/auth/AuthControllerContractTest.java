package com.loveos.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.auth.dto.AuthResponses;
import com.loveos.api.core.AppException;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.core.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;

class AuthControllerContractTest {

  private AuthService authService;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    authService = org.mockito.Mockito.mock(AuthService.class);
    TokenHasher tokenHasher = org.mockito.Mockito.mock(TokenHasher.class);
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    mvc = MockMvcBuilders
        .standaloneSetup(new AuthController(authService, tokenHasher))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setValidator(validator)
          .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .addFilters(new RequestIdFilter())
        .build();
  }

        @AfterEach
        void clearSecurityContext() {
          SecurityContextHolder.clearContext();
        }

  @Test
  void signupReturnsStableEnvelopeStatusAndRequestId() throws Exception {
    when(authService.signUp(any(), any())).thenReturn(session(false));

    mvc.perform(post("/v1/auth/signup")
            .header("x-request-id", "contract-test-123")
            .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"sam@example.com\",\"password\":\"Password1\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("x-request-id", "contract-test-123"))
        .andExpect(jsonPath("$.requestId").value("contract-test-123"))
        .andExpect(jsonPath("$.data.user.email").value("sam@example.com"))
        .andExpect(jsonPath("$.data.accessToken").value("access-token"))
        .andExpect(jsonPath("$.data.expiresIn").value(900))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
  }

  @Test
  void invalidSignupReturnsValidationEnvelope() throws Exception {
    mvc.perform(post("/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"not-email\",\"password\":\"short\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.issues").isArray())
        .andExpect(jsonPath("$.requestId").isString());
  }

  @Test
  void malformedJsonReturnsInvalidRequestEnvelope() throws Exception {
    mvc.perform(post("/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  void invalidCredentialsDoNotDiscloseAccountExistence() throws Exception {
    when(authService.login(any(), any())).thenThrow(AppException.invalidCredentials());

    mvc.perform(post("/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"missing@example.com\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.error.message").value("Email or password is incorrect"));
  }

  @Test
  void refreshReturnsRotatedSessionEnvelope() throws Exception {
    when(authService.refresh(eq("old-refresh"), any())).thenReturn(session(true));

    mvc.perform(post("/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"old-refresh\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.data.user.emailVerified").value(true));
  }

  @Test
  void logoutAlwaysReturnsNoContent() throws Exception {
    mvc.perform(post("/v1/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"refreshToken\":\"refresh-token\"}"))
        .andExpect(status().isNoContent())
        .andExpect(jsonPath("$").doesNotExist());

    verify(authService).logout("refresh-token");
  }

  @Test
  void forgotPasswordReturnsAcceptedWithoutAccountDisclosure() throws Exception {
    mvc.perform(post("/v1/auth/password/forgot")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"missing@example.com\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.accepted").value(true));
  }

  @Test
  void resetPasswordReturnsStableEnvelope() throws Exception {
    mvc.perform(post("/v1/auth/password/reset")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"reset-token\",\"newPassword\":\"NewPassword1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reset").value(true));
  }

  @Test
  void resendVerificationReturnsAccepted() throws Exception {
    mvc.perform(post("/v1/auth/verify-email/resend")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"sam@example.com\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.accepted").value(true));
  }

  @Test
  void verifyEmailReturnsUserWrapper() throws Exception {
    UUID userId = UUID.randomUUID();
    when(authService.verifyEmail("verify-token"))
        .thenReturn(new AuthResponses.UserDto(userId.toString(), "sam@example.com", true));

    mvc.perform(get("/v1/auth/verify-email").param("token", "verify-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.userId").value(userId.toString()))
        .andExpect(jsonPath("$.data.user.emailVerified").value(true));
  }

  @Test
  void currentUserUsesAuthenticatedPrincipalIdentity() throws Exception {
    UUID userId = UUID.randomUUID();
    when(authService.currentUser(userId))
        .thenReturn(new AuthResponses.UserDto(userId.toString(), "sam@example.com", true));
    var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
      new AuthenticatedUser(userId, "sam@example.com", true), null);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    mvc.perform(get("/v1/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.userId").value(userId.toString()));
  }

  private static AuthResponses.SessionDto session(boolean verified) {
    return new AuthResponses.SessionDto(
        new AuthResponses.UserDto(UUID.randomUUID().toString(), "sam@example.com", verified),
        "access-token",
        900,
        "refresh-token");
  }
}
