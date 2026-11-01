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
  void googleCreatesVerifiedAccountWithoutPasswordOrVerificationEmail() {
    var result = service.googleSignIn(new GoogleTokenVerifier.Identity("google-id", "New@Example.test", true), null,
        new AuthService.DeviceContext(null, null));
    assertThat(result.user().email()).isEqualTo("new@example.test");
    assertThat(result.user().emailVerified()).isTrue();
    var user = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(users).saveAndFlush(user.capture());
    assertThat(user.getValue().getGoogleSubject()).isEqualTo("google-id");
    assertThat(user.getValue().getPasswordHash()).isNull();
    org.mockito.Mockito.verifyNoInteractions(mailer);
  }

  @Test
  void googleDoesNotSilentlyLinkAnExistingEmail() {
    when(users.existsByEmail("existing@example.test")).thenReturn(true);
    assertThatThrownBy(() -> service.googleSignIn(new GoogleTokenVerifier.Identity("google-id", "existing@example.test"), null,
        new AuthService.DeviceContext(null, null)))
        .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.ACCOUNT_CONFLICT));
  }

  @Test
  void returningGoogleUserReusesTheSubjectBoundAccount() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("existing@gmail.com");
    user.setEmailVerified(true);
    user.setGoogleSubject("google-id");
    when(users.findByGoogleSubject("google-id")).thenReturn(Optional.of(user));
    var result = service.googleSignIn(new GoogleTokenVerifier.Identity("google-id", "existing@gmail.com"), null,
        new AuthService.DeviceContext(null, null));
    assertThat(result.user().userId()).isEqualTo(user.getId().toString());
    org.mockito.Mockito.verify(users, org.mockito.Mockito.never()).saveAndFlush(any(User.class));
  }

  @Test
  void thirdPartyGoogleEmailStillRequiresOurEmailVerification() {
    var result = service.googleSignIn(new GoogleTokenVerifier.Identity("google-id", "third-party@example.test", false), null,
        new AuthService.DeviceContext(null, null));
    assertThat(result.user().emailVerified()).isFalse();
    verify(mailer).sendVerification(eq("third-party@example.test"), any());
  }

  @Test
  void googleLinksOnlyTheSignedInVerifiedMatchingAccount() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setEmail("existing@example.test");
    user.setEmailVerified(true);
    user.setPasswordHash("keep-password");
    when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
    service.googleSignIn(new GoogleTokenVerifier.Identity("google-id", "existing@example.test"), userId,
        new AuthService.DeviceContext(null, null));
    assertThat(user.getGoogleSubject()).isEqualTo("google-id");
    assertThat(user.getPasswordHash()).isEqualTo("keep-password");
  }

  @Test
  void googleRejectsLinkingADifferentEmail() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setEmail("existing@example.test");
    user.setEmailVerified(true);
    when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
    assertThatThrownBy(() -> service.googleSignIn(new GoogleTokenVerifier.Identity("google-id", "other@example.test"), userId,
        new AuthService.DeviceContext(null, null))).isInstanceOf(AppException.class);
    assertThat(user.getGoogleSubject()).isNull();
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

  @Test
  void appleCreatesVerifiedRelayAccountWithoutPasswordOrVerificationEmail() {
    var result = service.appleSignIn(new AppleTokenVerifier.Identity("apple-id", "qa@privaterelay.appleid.com"), null,
        new AuthService.DeviceContext(null, null));
    assertThat(result.user().email()).isEqualTo("qa@privaterelay.appleid.com");
    assertThat(result.user().emailVerified()).isTrue();
    var user = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(users).saveAndFlush(user.capture());
    assertThat(user.getValue().getAppleSubject()).isEqualTo("apple-id");
    assertThat(user.getValue().getPasswordHash()).isNull();
    org.mockito.Mockito.verifyNoInteractions(mailer);
  }

  @Test
  void returningAppleUserIsIdentifiedBySubjectNotChangedEmail() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("old@privaterelay.appleid.com");
    user.setEmailVerified(true);
    user.setAppleSubject("apple-id");
    when(users.findByAppleSubject("apple-id")).thenReturn(Optional.of(user));
    var result = service.appleSignIn(new AppleTokenVerifier.Identity("apple-id", "new@privaterelay.appleid.com"), null,
        new AuthService.DeviceContext(null, null));
    assertThat(result.user().userId()).isEqualTo(user.getId().toString());
    org.mockito.Mockito.verify(users, org.mockito.Mockito.never()).saveAndFlush(any(User.class));
  }

  @Test
  void appleDoesNotMergeAnExistingEmailWithoutExplicitLinking() {
    when(users.existsByEmail("existing@example.test")).thenReturn(true);
    assertThatThrownBy(() -> service.appleSignIn(new AppleTokenVerifier.Identity("apple-id", "existing@example.test"), null,
        new AuthService.DeviceContext(null, null)))
        .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.ACCOUNT_CONFLICT));
  }

  @Test
  void verifiedSignedInUserCanExplicitlyLinkARelayAddress() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setEmail("existing@example.test");
    user.setEmailVerified(true);
    user.setPasswordHash("keep-password");
    when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
    service.appleSignIn(new AppleTokenVerifier.Identity("apple-id", "qa@privaterelay.appleid.com"), userId,
        new AuthService.DeviceContext(null, null));
    assertThat(user.getAppleSubject()).isEqualTo("apple-id");
    assertThat(user.getEmail()).isEqualTo("existing@example.test");
    assertThat(user.getPasswordHash()).isEqualTo("keep-password");
  }

  @Test
  void appleIdentityAlreadyOwnedByAnotherUserCannotBeLinked() {
    UUID userId = UUID.randomUUID();
    User current = new User();
    current.setId(userId);
    current.setEmailVerified(true);
    User other = new User();
    other.setId(UUID.randomUUID());
    when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(current));
    when(users.findByAppleSubject("apple-id")).thenReturn(Optional.of(other));
    assertThatThrownBy(() -> service.appleSignIn(new AppleTokenVerifier.Identity("apple-id", "qa@privaterelay.appleid.com"), userId,
        new AuthService.DeviceContext(null, null))).isInstanceOf(AppException.class);
    assertThat(current.getAppleSubject()).isNull();
  }
}
