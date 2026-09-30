package com.loveos.api.auth;

import com.loveos.api.auth.domain.EmailVerificationToken;
import com.loveos.api.auth.domain.PasswordResetToken;
import com.loveos.api.auth.domain.RefreshToken;
import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.dto.AuthRequests;
import com.loveos.api.auth.dto.AuthResponses;
import com.loveos.api.auth.repo.EmailVerificationTokenRepository;
import com.loveos.api.auth.repo.PasswordResetTokenRepository;
import com.loveos.api.auth.repo.RefreshTokenRepository;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.config.AppProperties;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.infra.Mailer;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * AUTHENTICATION.
 *
 * <p>A port of {@code ../ziyu_backend/src/modules/auth/auth.service.ts}. Behaviour
 * is identical because the client is already written against it; the notes below
 * record the reasoning that would otherwise be lost in translation.
 *
 * <p>NO WEB TYPES APPEAR IN THIS CLASS. No {@code HttpServletRequest}, no
 * {@code ResponseEntity}. That is a deliberate layering rule: everything here is
 * testable without a servlet container, and none of it can be tempted into
 * reading a header to decide a business question.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final EmailVerificationTokenRepository verificationTokens;
  private final PasswordResetTokenRepository resetTokens;
  private final PasswordEncoder passwordEncoder;
  private final TokenHasher tokenHasher;
  private final JwtService jwtService;
  private final Mailer mailer;
  private final AppProperties properties;

  /**
   * A valid hash of a value nothing will ever match, used to burn the same time
   * as a real comparison when the account does not exist.
   *
   * <p>It is produced by the injected encoder rather than pasted in as a literal
   * so that its cost factor is the real cost factor by construction. A
   * hard-coded hash silently stops working the day someone raises the strength
   * in {@code SecurityConfig}: the dummy comparison becomes measurably faster
   * than a genuine one, which is precisely the signal this exists to suppress.
   * One bcrypt at startup is a fair price for a defence that cannot rot.
   */
  private final String dummyHash;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      EmailVerificationTokenRepository verificationTokens,
      PasswordResetTokenRepository resetTokens,
      PasswordEncoder passwordEncoder,
      TokenHasher tokenHasher,
      JwtService jwtService,
      Mailer mailer,
      AppProperties properties) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.verificationTokens = verificationTokens;
    this.resetTokens = resetTokens;
    this.passwordEncoder = passwordEncoder;
    this.tokenHasher = tokenHasher;
    this.jwtService = jwtService;
    this.mailer = mailer;
    this.properties = properties;
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /** Context about the caller's device, for the session record. Never for authorisation. */
  public record DeviceContext(String userAgent, String ipHash) {}

  // ── Registration ────────────────────────────────────────────────────────────

  @Transactional
  public AuthResponses.SessionDto signUp(AuthRequests.SignUp request, DeviceContext device) {
    String email = normalise(request.email());

    // The friendly check. The unique constraint below is what actually enforces it.
    if (users.existsByEmail(email)) {
      throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, "That email is already registered");
    }

    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setEmailVerified(false);

    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException collision) {
      /*
       * Two simultaneous sign-ups for the same address both pass the check above
       * and both insert. The database rejects the loser, and it is reported as
       * the same conflict the check would have produced — the user sees one
       * coherent message rather than a 500 from a race they cannot observe.
       */
      throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS, "That email is already registered");
    }

    issueVerificationEmail(user);

    // The account is usable immediately; verification gates the routes that
    // need it. Blocking sign-in until an email arrives strands anyone whose
    // mail is slow, for no security gain at this point in the flow.
    return newSession(user, device);
  }

  // ── Sign-in ─────────────────────────────────────────────────────────────────

  @Transactional
  public AuthResponses.SessionDto login(AuthRequests.Login request, DeviceContext device) {
    String email = normalise(request.email());
    Optional<User> found = users.findByEmail(email);

    /*
     * TIMING. A missing account must cost the same as a wrong password, or the
     * response time answers "is this person registered?" — which for a couples
     * app is a genuinely sensitive question. So the hash is computed against a
     * dummy value rather than skipped.
     */
    if (found.isEmpty()) {
      passwordEncoder.matches(request.password(), dummyHash);
      throw AppException.invalidCredentials();
    }

    User user = found.get();

    // An OAuth-only account has no password to compare. Same error, so the
    // response does not reveal which sign-in methods an address has.
    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw AppException.invalidCredentials();
    }

    return newSession(user, device);
  }

  // ── Refresh ─────────────────────────────────────────────────────────────────

  /**
   * Exchanges a refresh token for a new session, rotating it.
   *
   * <p>REUSE DETECTION. A token that has already been rotated should never be
   * presented again. If it is, either the client replayed an old request or
   * somebody stole it — and there is no way to tell which from here. The safe
   * reading is theft, so every session for that user is revoked. Both parties
   * sign in again; only one of them is inconvenienced undeservedly.
   */
  @Transactional
  public AuthResponses.SessionDto refresh(String presentedToken, DeviceContext device) {
    if (presentedToken == null || presentedToken.isBlank()) {
      throw AppException.tokenInvalid("No refresh token supplied");
    }

    RefreshToken stored = refreshTokens
        .findByTokenHash(tokenHasher.hash(presentedToken))
        .orElseThrow(() -> AppException.tokenInvalid("Refresh token is not recognised"));

    Instant now = Instant.now();

    if (stored.getReplacedById() != null) {
      log.warn("refresh token reuse detected for user {} - revoking all sessions",
          stored.getUserId());
      refreshTokens.revokeAllForUser(stored.getUserId(), now);
      throw AppException.tokenInvalid("Refresh token has already been used");
    }

    if (stored.getRevokedAt() != null) {
      throw AppException.tokenInvalid("Refresh token has been revoked");
    }

    if (!stored.getExpiresAt().isAfter(now)) {
      throw AppException.tokenExpired("Refresh token has expired");
    }

    User user = users.findById(stored.getUserId())
        .orElseThrow(() -> AppException.tokenInvalid("Account no longer exists"));

    // Mint the successor first, then point the old row at it: if anything fails
    // in between, the old token stays usable rather than the user losing their
    // session to a half-completed rotation.
    IssuedRefreshToken issued = persistRefreshToken(user.getId(), device);
    stored.setReplacedById(issued.getId());
    stored.setRevokedAt(now);
    refreshTokens.save(stored);

    return session(user, issued.getRawToken());
  }

  // ── Sign-out ────────────────────────────────────────────────────────────────

  /**
   * Revokes one session. Never throws.
   *
   * <p>The endpoint answers 204 whatever happens, because the client clears its
   * local session regardless — a failed revocation must not leave someone
   * apparently signed in on the device in front of them.
   */
  @Transactional
  public void logout(String presentedToken) {
    if (presentedToken == null || presentedToken.isBlank()) {
      return;
    }
    refreshTokens.findByTokenHash(tokenHasher.hash(presentedToken))
        .ifPresent(token -> {
          token.setRevokedAt(Instant.now());
          refreshTokens.save(token);
        });
  }

  // ── Email verification ──────────────────────────────────────────────────────

  @Transactional
  public void resendVerification(String rawEmail) {
    String email = normalise(rawEmail);
    Optional<User> found = users.findByEmail(email);

    // Silent for an unknown address — see requestPasswordReset for the reasoning.
    if (found.isEmpty()) {
      return;
    }

    User user = found.get();
    if (user.isEmailVerified()) {
      // Safe to report: the caller already proved they know this address is
      // registered by having an account, and "already verified" is actionable.
      throw new AppException(ErrorCode.EMAIL_ALREADY_VERIFIED, "This email is already verified");
    }

    issueVerificationEmail(user);
  }

  @Transactional
  public AuthResponses.UserDto verifyEmail(String rawToken) {
    EmailVerificationToken token = verificationTokens
        .findByTokenHash(tokenHasher.hash(rawToken))
        .orElseThrow(() -> AppException.tokenInvalid("This link is not valid"));

    Instant now = Instant.now();

    if (token.getConsumedAt() != null) {
      throw new AppException(ErrorCode.EMAIL_ALREADY_VERIFIED, "This email is already verified");
    }
    if (!token.getExpiresAt().isAfter(now)) {
      throw AppException.tokenExpired("This link has expired");
    }

    User user = users.findById(token.getUserId())
        .orElseThrow(() -> AppException.tokenInvalid("Account no longer exists"));

    user.setEmailVerified(true);
    users.save(user);

    token.setConsumedAt(now);
    verificationTokens.save(token);

    return toDto(user);
  }

  // ── Password reset ──────────────────────────────────────────────────────────

  /**
   * Always succeeds from the caller's point of view.
   *
   * <p>Reporting "no such account" here would turn this endpoint into a
   * membership oracle: anyone could test an address and learn whether its owner
   * uses the app. For a product about relationships that is a real disclosure,
   * so the response is identical either way and the work simply does not happen
   * for an unknown address.
   */
  @Transactional
  public void requestPasswordReset(String rawEmail) {
    String email = normalise(rawEmail);
    Optional<User> found = users.findByEmail(email);
    if (found.isEmpty()) {
      log.debug("password reset requested for unknown address");
      return;
    }

    User user = found.get();
    resetTokens.deleteAllByUserId(user.getId());

    String raw = tokenHasher.generate(properties.tokens().opaqueTokenBytes());
    PasswordResetToken token = new PasswordResetToken();
    token.setUserId(user.getId());
    token.setTokenHash(tokenHasher.hash(raw));
    token.setExpiresAt(Instant.now().plus(properties.tokens().passwordResetTtl()));
    resetTokens.save(token);

    mailer.sendPasswordReset(user.getEmail(), raw);
  }

  @Transactional
  public void resetPassword(AuthRequests.ResetPassword request) {
    PasswordResetToken token = resetTokens
        .findByTokenHash(tokenHasher.hash(request.token()))
        .orElseThrow(() -> AppException.tokenInvalid("This reset link is not valid"));

    Instant now = Instant.now();

    if (token.getConsumedAt() != null) {
      throw AppException.tokenInvalid("This reset link has already been used");
    }
    if (!token.getExpiresAt().isAfter(now)) {
      throw AppException.tokenExpired("This reset link has expired");
    }

    User user = users.findById(token.getUserId())
        .orElseThrow(() -> AppException.tokenInvalid("Account no longer exists"));

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    users.save(user);

    token.setConsumedAt(now);
    resetTokens.save(token);

    /*
     * Every session dies, including the caller's. If the reset happened because
     * the account was compromised, leaving the attacker's refresh token alive
     * would make the whole exercise pointless. The client is told to sign in
     * again, which also confirms the new password works.
     */
    int revoked = refreshTokens.revokeAllForUser(user.getId(), now);
    log.info("password reset for user {} revoked {} sessions", user.getId(), revoked);
  }

  // ── Reads ───────────────────────────────────────────────────────────────────

  public AuthResponses.UserDto currentUser(UUID userId) {
    return users.findById(userId)
        .map(AuthService::toDto)
        .orElseThrow(() -> AppException.unauthorized("Account no longer exists"));
  }

  // ── Internals ───────────────────────────────────────────────────────────────

  /**
   * Lower-cased and trimmed.
   *
   * <p>{@code Locale.ROOT} is not decoration: with a Turkish default locale,
   * {@code "I".toLowerCase()} yields a dotless ı, so the same address would
   * normalise differently depending on where the server runs and a user could
   * fail to sign in after a deployment moved regions.
   */
  private static String normalise(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static AuthResponses.UserDto toDto(User user) {
    return new AuthResponses.UserDto(
        user.getId().toString(), user.getEmail(), user.isEmailVerified());
  }

  private void issueVerificationEmail(User user) {
    verificationTokens.deleteAllByUserId(user.getId());

    String raw = tokenHasher.generate(properties.tokens().opaqueTokenBytes());
    EmailVerificationToken token = new EmailVerificationToken();
    token.setUserId(user.getId());
    token.setTokenHash(tokenHasher.hash(raw));
    token.setExpiresAt(Instant.now().plus(properties.tokens().emailVerificationTtl()));
    verificationTokens.save(token);

    mailer.sendVerification(user.getEmail(), raw);
  }

  private AuthResponses.SessionDto newSession(User user, DeviceContext device) {
    return session(user, persistRefreshToken(user.getId(), device).getRawToken());
  }

  private AuthResponses.SessionDto session(User user, String rawRefreshToken) {
    String accessToken =
        jwtService.issueAccessToken(user.getId(), user.getEmail(), user.isEmailVerified());

    return new AuthResponses.SessionDto(
        toDto(user), accessToken, jwtService.accessTokenSeconds(), rawRefreshToken);
  }

  /**
   * Stores a new refresh session and returns it with the raw token attached.
   *
   * <p>The raw value is carried on a transient field because it exists for the
   * duration of this call and must never be persisted — the row holds only the
   * digest. Returning a pair instead would be equivalent; this keeps the call
   * sites readable.
   */
  private IssuedRefreshToken persistRefreshToken(UUID userId, DeviceContext device) {
    String raw = tokenHasher.generate(properties.tokens().opaqueTokenBytes());

    RefreshToken token = new RefreshToken();
    token.setUserId(userId);
    token.setTokenHash(tokenHasher.hash(raw));
    token.setExpiresAt(Instant.now().plus(properties.jwt().refreshTokenTtl()));
    token.setUserAgent(truncate(device.userAgent()));
    token.setIpHash(device.ipHash());

    return new IssuedRefreshToken(refreshTokens.save(token), raw);
  }

  /** A User-Agent is client-controlled and unbounded; the column is not. */
  private static String truncate(String value) {
    if (value == null) return null;
    return value.length() <= 255 ? value : value.substring(0, 255);
  }

  private record IssuedRefreshToken(RefreshToken entity, String rawToken) {
    UUID getId() {
      return entity.getId();
    }

    String getRawToken() {
      return rawToken;
    }
  }

  /** Removes rows that can no longer authenticate anything. */
  @Transactional
  public int purgeExpiredTokens() {
    return refreshTokens.deleteExpiredBefore(Instant.now());
  }
}
