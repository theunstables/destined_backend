package com.loveos.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * THE TRUST BOUNDARY — every shape the client may send, and the rules each must
 * satisfy.
 *
 * <p>Records rather than classes with setters: a request object should be
 * complete and immutable the moment it is constructed. Nothing downstream
 * re-validates, which is only safe because binding fails before a controller
 * body ever runs.
 *
 * <p>MASS ASSIGNMENT IS PREVENTED BY CONSTRUCTION. These records name exactly the
 * fields a client may supply, and Jackson ignores the rest. A request containing
 * {@code "emailVerified": true} cannot set it, because there is nowhere for that
 * value to land — which is why entities are never bound directly to a request
 * body (OWASP A08).
 */
public final class AuthRequests {

  private AuthRequests() {}

  /**
   * The password policy: 8–128 characters, at least one letter and one digit.
   *
   * <p>Deliberately not a maze of symbol requirements. Long-standing NIST
   * guidance is that composition rules push people towards {@code Password1!}
   * and a sticky note, while length does the real work. The 128 ceiling is a
   * denial-of-service guard: bcrypt's cost is bounded by input size, and an
   * unbounded password is an unbounded hash.
   */
  private static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,128}$";

  private static final String PASSWORD_MESSAGE =
      "Use 8–128 characters with at least one letter and one number";

  public record SignUp(
      @NotBlank @Email(message = "Enter a valid email address") @Size(max = 254) String email,
      @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password) {}

  /**
   * Login does NOT apply the password pattern.
   *
   * <p>Rejecting a malformed password before checking it would tell an attacker
   * which policy an existing account was created under, and would lock out
   * anyone whose valid old password no longer meets a tightened rule. Login
   * checks the credential; only sign-up and reset enforce the policy.
   */
  public record Login(
      @NotBlank @Email(message = "Enter a valid email address") String email,
      @NotBlank @Size(max = 128) String password) {}

  /** {@code refreshToken} is optional: web clients send the httpOnly cookie instead. */
  public record Refresh(String refreshToken) {}

  public record Logout(String refreshToken) {}

  public record EmailOnly(
      @NotBlank @Email(message = "Enter a valid email address") String email) {}

  public record ResetPassword(
      @NotBlank String token,
      @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {}
}
