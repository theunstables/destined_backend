package com.loveos.api.core;

/**
 * THE CLOSED SET OF ERROR CODES.
 *
 * <p>Ported verbatim from {@code ../ziyu_backend/docs/ERROR_CODES.md}. The client
 * maps a code to its own copy and never displays the server's message, so these
 * strings are part of the API contract in a way that prose never is. Renaming one
 * breaks a screen; adding one that the client does not know produces
 * {@code UNKNOWN} at the other end.
 *
 * <p>An enum rather than loose strings so a typo is a compile error, and so the
 * HTTP status travels with the code instead of being re-decided at each throw
 * site. Two handlers disagreeing about whether an expired invite is 404 or 410
 * is exactly the drift this prevents.
 *
 * <p>The client's {@code AuthErrorCode} union additionally contains {@code NETWORK}
 * and {@code UNKNOWN}. Those are deliberately absent here: one means the request
 * never arrived, the other means the answer was unrecognisable. Neither is
 * something a server can report about itself.
 */
public enum ErrorCode {

  // ── Request shape ─────────────────────────────────────────────────────────
  INVALID_REQUEST(400),
  /** Carries {@code issues[]}. 422 because the body parsed but failed the rules. */
  VALIDATION_ERROR(422),
  PAYLOAD_TOO_LARGE(413),
  UNSUPPORTED_MEDIA_TYPE(415),

  // ── Authentication ────────────────────────────────────────────────────────
  INVALID_EMAIL(400),
  WEAK_PASSWORD(400),
  /** Wrong password *and* no such account — deliberately indistinguishable. */
  INVALID_CREDENTIALS(401),
  INVALID_PROVIDER_TOKEN(401),
  TOKEN_INVALID(401),
  /** Valid but expired: the client should refresh and retry, not sign out. */
  TOKEN_EXPIRED(401),
  UNAUTHORIZED(401),
  EMAIL_NOT_VERIFIED(403),
  FORBIDDEN(403),
  EMAIL_ALREADY_EXISTS(409),
  EMAIL_ALREADY_VERIFIED(409),
  ACCOUNT_CONFLICT(409),

  // ── Pairing ───────────────────────────────────────────────────────────────
  CODE_INVALID(404),
  CODE_EXPIRED(410),
  CANNOT_PAIR_WITH_SELF(409),
  ALREADY_PAIRED(409),
  NO_COUPLE(403),

  // ── Content ───────────────────────────────────────────────────────────────
  NOT_FOUND(404),
  CONFLICT(409),

  // ── Infrastructure ────────────────────────────────────────────────────────
  RATE_LIMITED(429),
  INTERNAL_ERROR(500);

  private final int status;

  ErrorCode(int status) {
    this.status = status;
  }

  public int status() {
    return status;
  }
}
