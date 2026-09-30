package com.loveos.api.auth;

import com.loveos.api.config.AppProperties;
import com.loveos.api.core.AppException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the short-lived access token.
 *
 * <p>WHY A JWT FOR ACCESS AND AN OPAQUE TOKEN FOR REFRESH. They have opposite
 * requirements. An access token is presented on every request, so verifying it
 * must not touch the database — a signature check is enough, and that is exactly
 * what a JWT gives. A refresh token must be *revocable* the instant something
 * goes wrong, which a self-contained token cannot be; so it is a random string
 * whose digest lives in a table that can be updated. Using a JWT for refresh
 * would mean a stolen token stays valid for thirty days with no way to stop it.
 *
 * <p>THE CONSEQUENCE, STATED PLAINLY: an access token cannot be revoked before it
 * expires. That is the price of stateless verification, and it is why the
 * lifetime is fifteen minutes rather than a day.
 *
 * <p>{@code emailVerified} is a claim so that {@code requireVerified} does not
 * need a user lookup. It can be at most fifteen minutes stale, which is
 * acceptable: the worst case is that someone who just confirmed their address
 * waits briefly, or that a freshly unverified account keeps access until its
 * token expires.
 */
@Service
public class JwtService {

  private final SecretKey key;
  private final AppProperties properties;

  public JwtService(AppProperties properties) {
    this.properties = properties;
    // Throws if the secret is too short for HS256 — a fail-fast we want.
    this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
  }

  /** The verified contents of an access token. */
  public record AccessClaims(UUID userId, String email, boolean emailVerified) {}

  public String issueAccessToken(UUID userId, String email, boolean emailVerified) {
    Instant now = Instant.now();
    Instant expiry = now.plus(properties.jwt().accessTokenTtl());

    return Jwts.builder()
        .subject(userId.toString())
        .issuer(properties.jwt().issuer())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .claim("email", email)
        .claim("emailVerified", emailVerified)
        .signWith(key)
        .compact();
  }

  public long accessTokenSeconds() {
    return properties.jwt().accessTokenTtl().toSeconds();
  }

  /**
   * Verifies signature, expiry and issuer.
   *
   * <p>Expiry is reported as {@code TOKEN_EXPIRED} and everything else as
   * {@code TOKEN_INVALID}, because the client treats them completely differently:
   * the first means "refresh and retry", the second means "sign in again".
   * Collapsing them would make every brief expiry look like a session loss.
   */
  public AccessClaims verify(String token) {
    try {
      Claims claims = Jwts.parser()
          .verifyWith(key)
          .requireIssuer(properties.jwt().issuer())
          .build()
          .parseSignedClaims(token)
          .getPayload();

      return new AccessClaims(
          UUID.fromString(claims.getSubject()),
          claims.get("email", String.class),
          Boolean.TRUE.equals(claims.get("emailVerified", Boolean.class)));

    } catch (ExpiredJwtException expired) {
      throw AppException.tokenExpired("Access token has expired");
    } catch (JwtException | IllegalArgumentException invalid) {
      // Covers a bad signature, a tampered payload, a wrong issuer and a
      // malformed subject. The client is told nothing beyond "invalid" — which
      // of those it was is useful to an attacker and to nobody else.
      throw AppException.tokenInvalid("Access token is not valid");
    }
  }
}
