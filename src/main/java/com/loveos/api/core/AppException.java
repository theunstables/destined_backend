package com.loveos.api.core;

/**
 * The one exception the application throws on purpose.
 *
 * <p>Carrying an {@link ErrorCode} rather than a status means a service can refuse
 * a request without importing anything web-related — the service layer knows
 * that an email is taken, not that the answer is 409. The translation happens
 * once, in {@link GlobalExceptionHandler}.
 *
 * <p>Extends {@code RuntimeException} so a business rule does not have to be
 * declared on every intermediate signature. A checked exception here would push
 * {@code throws} clauses through layers that have nothing to add to them.
 *
 * <p>The stack trace is suppressed. These are expected outcomes — a wrong
 * password is not an incident — and filling in a trace for each one costs real
 * time on a hot path while producing logs nobody reads.
 */
public class AppException extends RuntimeException {

  private final ErrorCode code;

  public AppException(ErrorCode code, String message) {
    super(message, null, false, false);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }

  // ── Constructors for the cases that recur ──────────────────────────────────
  // Named so the call site reads as the rule being enforced rather than as
  // exception plumbing.

  public static AppException unauthorized(String message) {
    return new AppException(ErrorCode.UNAUTHORIZED, message);
  }

  public static AppException invalidCredentials() {
    // Never "no such user". Distinguishing the two turns the login form into an
    // account-enumeration oracle.
    return new AppException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
  }

  public static AppException tokenInvalid(String message) {
    return new AppException(ErrorCode.TOKEN_INVALID, message);
  }

  public static AppException tokenExpired(String message) {
    return new AppException(ErrorCode.TOKEN_EXPIRED, message);
  }

  public static AppException notFound(String message) {
    return new AppException(ErrorCode.NOT_FOUND, message);
  }

  public static AppException conflict(ErrorCode code, String message) {
    return new AppException(code, message);
  }
}
