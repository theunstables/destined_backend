package com.loveos.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loveos.api.auth.domain.RefreshToken;
import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.dto.AuthRequests;
import com.loveos.api.auth.repo.EmailVerificationTokenRepository;
import com.loveos.api.auth.repo.PasswordResetTokenRepository;
import com.loveos.api.auth.repo.RefreshTokenRepository;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.config.AppProperties;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.infra.Mailer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

  private UserRepository users;
  private RefreshTokenRepository refreshTokens;
  private EmailVerificationTokenRepository verificationTokens;
  private PasswordEncoder passwordEncoder;
  private TokenHasher tokenHasher;
  private JwtService jwtService;
  private Mailer mailer;
  private AuthService service;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    refreshTokens = mock(RefreshTokenRepository.class);
    verificationTokens = mock(EmailVerificationTokenRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    tokenHasher = mock(TokenHasher.class);
    jwtService = mock(JwtService.class);
    mailer = mock(Mailer.class);

    when(passwordEncoder.encode(any())).thenReturn("encoded-password");
    when(tokenHasher.generate(32)).thenReturn("raw-verification", "raw-refresh");
    when(tokenHasher.hash(any())).thenAnswer(call -> "hash-" + call.getArgument(0));
    when(jwtService.issueAccessToken(any(), any(), any(Boolean.class))).thenReturn("access-token");
    when(jwtService.accessTokenSeconds()).thenReturn(900L);
    doAnswer(call -> {
      User user = call.getArgument(0);
      if (user.getId() == null) user.setId(UUID.randomUUID());
      return user;
    }).when(users).saveAndFlush(any(User.class));
    when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(call -> {
      RefreshToken token = call.getArgument(0);
      if (token.getId() == null) token.setId(UUID.randomUUID());
      return token;
    });

    service = new AuthService(
        users,
        refreshTokens,
        verificationTokens,
        mock(PasswordResetTokenRepository.class),
        passwordEncoder,
        tokenHasher,
        jwtService,
        mailer,
        properties());
  }

  @Test
  void signupNormalizesEmailAndCreatesVerificationAndSession() {
    var result = service.signUp(
        new AuthRequests.SignUp("  Sam@Example.COM ", "Password1"),
        new AuthService.DeviceContext("test-agent", "ip-hash"));

    assertThat(result.user().email()).isEqualTo("sam@example.com");
    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isEqualTo("raw-refresh");
    verify(users).existsByEmail("sam@example.com");
    verify(verificationTokens).deleteAllByUserId(result.user().userId() == null
        ? null : UUID.fromString(result.user().userId()));
    verify(mailer).sendVerification("sam@example.com", "raw-verification");
  }

  @Test
  void duplicateSignupUsesContractError() {
    when(users.existsByEmail("sam@example.com")).thenReturn(true);

    assertThatThrownBy(() -> service.signUp(
        new AuthRequests.SignUp("sam@example.com", "Password1"),
        new AuthService.DeviceContext(null, null)))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
  }

  @Test
  void missingAccountStillRunsPasswordComparisonAndReturnsGenericError() {
    when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login(
        new AuthRequests.Login("missing@example.com", "wrong"),
        new AuthService.DeviceContext(null, null)))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));

    verify(passwordEncoder).matches("wrong", "encoded-password");
  }

  @Test
  void replayingRotatedRefreshTokenRevokesEverySession() {
    UUID userId = UUID.randomUUID();
    RefreshToken stored = new RefreshToken();
    stored.setUserId(userId);
    stored.setExpiresAt(Instant.now().plusSeconds(3600));
    stored.setReplacedById(UUID.randomUUID());
    when(refreshTokens.findByTokenHash("hash-old-refresh")).thenReturn(Optional.of(stored));

    assertThatThrownBy(() -> service.refresh(
        "old-refresh", new AuthService.DeviceContext(null, null)))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.TOKEN_INVALID));

    verify(refreshTokens).revokeAllForUser(eq(userId), any(Instant.class));
  }

  private static AppProperties properties() {
    return new AppProperties(
        new AppProperties.Jwt(
            "test-only-secret-with-at-least-32-characters",
            "loveos-test",
            Duration.ofMinutes(15),
            Duration.ofDays(30)),
        new AppProperties.Tokens(Duration.ofHours(24), Duration.ofMinutes(30), 32),
        new AppProperties.Mail("LoveOS <test@loveos.invalid>", "console"),
        "http://localhost:4000");
  }
}
