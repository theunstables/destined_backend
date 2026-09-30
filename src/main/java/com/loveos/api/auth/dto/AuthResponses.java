package com.loveos.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Everything this module sends back.
 *
 * <p>These are shaped by {@code ../ziyu_backend/docs/API.md} and by the Expo
 * client's {@code Session} type, not by the entities behind them. That
 * separation is the point: {@link AuthResponses.UserDto} exposes three fields,
 * so {@code passwordHash} cannot leak by someone adding a getter to the entity.
 *
 * <p>{@code userId} rather than {@code id}, because that is what the client's
 * {@code Session} declares. Matching the existing type exactly is what allows
 * the mock service to be swapped for the real one without touching a screen.
 */
public final class AuthResponses {

  private AuthResponses() {}

  public record UserDto(String userId, String email, boolean emailVerified) {}

  /**
   * The sign-in / sign-up / refresh payload.
   *
   * <p>{@code expiresIn} is seconds, matching OAuth 2 convention and what the
   * client's token store expects. An absolute timestamp would be worse: it would
   * depend on the device's clock agreeing with the server's, and phone clocks
   * drift. A duration is immune to that.
   */
  public record SessionDto(
      UserDto user,
      String accessToken,
      long expiresIn,
      @JsonInclude(JsonInclude.Include.NON_NULL) String refreshToken) {}

  /** For the endpoints that must not reveal whether an account exists. */
  public record AcceptedDto(boolean accepted) {}

  public record ResetDto(boolean reset) {}

  public record UserWrapper(UserDto user) {}
}
