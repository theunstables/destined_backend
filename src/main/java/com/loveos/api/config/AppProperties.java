package com.loveos.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated application settings.
 *
 * <p>VALIDATION HERE MEANS THE APPLICATION REFUSES TO START rather than
 * misbehaving later. A blank JWT secret is not a runtime error to be discovered
 * when the first token is issued — it is a deployment that should never have
 * come up. {@code @Validated} turns a bad value into a startup failure with the
 * property name in the message.
 *
 * <p>The 32-character minimum on the secret is the security-relevant constraint.
 * HS256 keys shorter than the 256-bit output of the hash weaken the signature,
 * and a memorable secret is a guessable one. JJWT refuses undersized keys
 * outright; catching it here gives a message that says which property is wrong.
 *
 * <p>EVERY NESTED RECORD IS ANNOTATED {@code @Valid}. Bean Validation does not
 * descend into an object unless told to, so without it the constraints below are
 * declared but never evaluated — and a validation rule that silently does not
 * run is worse than none, because it is trusted. This was not hypothetical: an
 * unresolved {@code ${JWT_SECRET}} placeholder reached {@code JwtService} as a
 * 13-character literal and was caught by JJWT's key-length check rather than by
 * the rule written here to catch it.
 */
@ConfigurationProperties(prefix = "loveos")
@Validated
public record AppProperties(
    @Valid Jwt jwt, @Valid Tokens tokens, @Valid Mail mail, String publicApiUrl) {

  public record Jwt(
      @NotBlank
      @Size(min = 32, message = "JWT secret must be at least 32 characters")
      String secret,
      @NotBlank String issuer,
      /**
       * Fifteen minutes. Long enough that refreshes are rare, short enough that a
       * leaked access token is worthless before it can be used at leisure. The
       * client refreshes pre-emptively, so this is invisible to a user.
       */
      Duration accessTokenTtl,
      /** Thirty days — how long someone may stay signed in without re-entering a password. */
      Duration refreshTokenTtl) {}

  public record Tokens(
      Duration emailVerificationTtl,
      Duration passwordResetTtl,
      @Positive int opaqueTokenBytes) {}

  public record Mail(@NotBlank String from, @NotBlank String driver) {}
}
