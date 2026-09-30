package com.loveos.api.auth;

import com.loveos.api.auth.dto.AuthRequests;
import com.loveos.api.auth.dto.AuthResponses;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * THE AUTH HTTP SURFACE.
 *
 * <p>Paths, status codes and body shapes are fixed by
 * {@code ../ziyu_backend/docs/API.md} and by the Expo client already written
 * against them. Changing any of them here breaks a shipped app, so they are
 * transcribed rather than reinvented.
 *
 * <p>THIS CLASS CONTAINS NO RULES. It translates HTTP into a call and a result
 * back into HTTP. Every decision — whether a password is right, whether a token
 * is live — belongs to {@link AuthService}, which is why that class can be tested
 * without a servlet container. A conditional appearing here is a sign that logic
 * has leaked upwards.
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final TokenHasher tokenHasher;

  public AuthController(AuthService authService, TokenHasher tokenHasher) {
    this.authService = authService;
    this.tokenHasher = tokenHasher;
  }

  // ── Public ──────────────────────────────────────────────────────────────────

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AuthResponses.SessionDto> signUp(
      @Valid @RequestBody AuthRequests.SignUp request, HttpServletRequest http) {
    // 201, not 200: a new resource exists that did not before.
    return ApiResponse.of(authService.signUp(request, device(http)));
  }

  @PostMapping("/login")
  public ApiResponse<AuthResponses.SessionDto> login(
      @Valid @RequestBody AuthRequests.Login request, HttpServletRequest http) {
    return ApiResponse.of(authService.login(request, device(http)));
  }

  @PostMapping("/refresh")
  public ApiResponse<AuthResponses.SessionDto> refresh(
      @RequestBody(required = false) AuthRequests.Refresh request, HttpServletRequest http) {
    /*
     * The body is optional because a web client sends the token in an httpOnly
     * cookie instead — a token the page's JavaScript cannot read, and therefore
     * cannot lose to XSS. The body is preferred when both are present, since a
     * client that bothered to send one is the one being explicit.
     */
    String presented = request == null ? null : request.refreshToken();
    if (presented == null || presented.isBlank()) {
      presented = cookie(http, REFRESH_COOKIE);
    }
    return ApiResponse.of(authService.refresh(presented, device(http)));
  }

  /**
   * Always 204, even when the token was already dead.
   *
   * <p>Logout is the one operation that must never fail. The client discards its
   * session the moment it calls this; an error here would leave the user looking
   * at a "couldn't sign out" message while already signed out, and — worse —
   * might tempt a client into retaining the token. Revocation failures are
   * swallowed inside the service and logged there.
   */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
      @RequestBody(required = false) AuthRequests.Logout request, HttpServletRequest http) {
    String presented = request == null ? null : request.refreshToken();
    if (presented == null || presented.isBlank()) {
      presented = cookie(http, REFRESH_COOKIE);
    }
    authService.logout(presented);
  }

  /**
   * 202 Accepted, unconditionally.
   *
   * <p>"Accepted" describes exactly what happened: the request was taken, and
   * whether an email follows is not disclosed. A 404 for an unknown address would
   * turn this endpoint into a membership oracle — ask it about a thousand
   * addresses and learn which belong to users of a couples app, which is
   * genuinely sensitive information about people's private lives.
   */
  @PostMapping("/password/forgot")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiResponse<AuthResponses.AcceptedDto> forgotPassword(
      @Valid @RequestBody AuthRequests.EmailOnly request) {
    authService.requestPasswordReset(request.email());
    return ApiResponse.of(new AuthResponses.AcceptedDto(true));
  }

  @PostMapping("/password/reset")
  public ApiResponse<AuthResponses.ResetDto> resetPassword(
      @Valid @RequestBody AuthRequests.ResetPassword request) {
    authService.resetPassword(request);
    // No session is returned. Whoever just changed the password proved control
    // of the inbox, not of the account; signing in again closes that gap.
    return ApiResponse.of(new AuthResponses.ResetDto(true));
  }

  @PostMapping("/verify-email/resend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApiResponse<AuthResponses.AcceptedDto> resendVerification(
      @Valid @RequestBody AuthRequests.EmailOnly request) {
    authService.resendVerification(request.email());
    return ApiResponse.of(new AuthResponses.AcceptedDto(true));
  }

  /**
   * The target of the emailed link, so it is a GET with a query parameter.
   *
   * <p>That is a compromise, not an oversight: a GET that mutates state is
   * ordinarily wrong, but an email client cannot POST. The token is
   * single-use and short-lived, which limits what a prefetching mail scanner or
   * a leaked Referer can do with it.
   */
  @GetMapping("/verify-email")
  public ApiResponse<AuthResponses.UserWrapper> verifyEmail(@RequestParam("token") String token) {
    return ApiResponse.of(new AuthResponses.UserWrapper(authService.verifyEmail(token)));
  }

  // ── Authenticated ───────────────────────────────────────────────────────────

  /**
   * The identity comes from the principal the filter built out of a signed token.
   *
   * <p>There is deliberately no {@code userId} path or query parameter anywhere in
   * this API. If the caller could name the user, every handler would need an
   * ownership check and one of them would eventually be forgotten — that is
   * broken access control (OWASP A01) waiting to happen. Taking it from the
   * signature makes the whole class of bug unreachable.
   */
  @GetMapping("/me")
  public ApiResponse<AuthResponses.UserWrapper> me(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    if (principal == null) {
      // Defence in depth: the filter chain already requires authentication here,
      // so this only fires if the security config is edited wrongly. Better a
      // clean 401 than a NullPointerException surfacing as a 500.
      throw AppException.unauthorized("Sign in to continue");
    }
    return ApiResponse.of(new AuthResponses.UserWrapper(authService.currentUser(principal.userId())));
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private static final String REFRESH_COOKIE = "loveos_refresh";

  /**
   * Builds the audit context stored against a session.
   *
   * <p>THE IP IS HASHED, NEVER STORED RAW. It is enough to notice that a refresh
   * token is suddenly being used from somewhere new, which is all this is for,
   * while a database leak yields no location history. Under GDPR an IP address is
   * personal data; a hash of one is far less useful to an attacker and still
   * answers the only question asked of it.
   *
   * <p>The user agent is truncated because it is attacker-controlled and
   * unbounded — a client can send a megabyte of it, and that should not become a
   * megabyte row.
   */
  private AuthService.DeviceContext device(HttpServletRequest http) {
    String userAgent = http.getHeader("User-Agent");
    if (userAgent != null && userAgent.length() > 256) {
      userAgent = userAgent.substring(0, 256);
    }

    String remote = http.getRemoteAddr();
    String ipHash = remote == null ? null : tokenHasher.hash(remote);

    return new AuthService.DeviceContext(userAgent, ipHash);
  }

  private static String cookie(HttpServletRequest http, String name) {
    var cookies = http.getCookies();
    if (cookies == null) return null;
    for (var cookie : cookies) {
      if (name.equals(cookie.getName())) return cookie.getValue();
    }
    return null;
  }
}
