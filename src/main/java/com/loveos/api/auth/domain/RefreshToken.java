package com.loveos.api.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A refresh session.
 *
 * <p>THE RAW TOKEN IS NEVER STORED. Only a SHA-256 digest is, so a dump of this
 * table cannot be replayed as a login — the same reasoning as password hashing,
 * applied to a credential that is arguably more valuable because it is silent.
 * Lookup is by digest, which is why {@code tokenHash} is unique and indexed.
 *
 * <p>ROTATION AND REUSE DETECTION. Every refresh issues a new token and sets
 * {@code replacedById} on the old one. Presenting a token that already has a
 * successor means two parties hold it, and only one of them can be legitimate —
 * so the entire family is revoked and both are forced to sign in again. Locking
 * a user out of their session is the correct response to evidence of theft; the
 * alternative is letting a thief refresh indefinitely.
 */
@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
      @Index(name = "idx_refresh_user", columnList = "user_id"),
      // Supports the periodic sweep of expired rows without a full scan.
      @Index(name = "idx_refresh_expires", columnList = "expires_at")
    })
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  /** SHA-256 of the opaque token, hex-encoded. Never the token itself. */
  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /** Set on logout, on password reset, and on reuse detection. */
  @Column(name = "revoked_at")
  private Instant revokedAt;

  /** Points at this token's successor once rotated. Null while current. */
  @Column(name = "replaced_by_id", columnDefinition = "uuid")
  private UUID replacedById;

  @Column(name = "user_agent", length = 256)
  private String userAgent;

  /**
   * A hash, not the address.
   *
   * <p>It is enough to tell "same device as last time" from "somewhere new",
   * which is all the security value an IP has here, without keeping personal
   * location data that would otherwise sit in this table for thirty days.
   */
  @Column(name = "ip_hash", length = 64)
  private String ipHash;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Usable means: not revoked, not yet expired, and not already rotated. */
  public boolean isActive(Instant now) {
    return revokedAt == null && replacedById == null && expiresAt.isAfter(now);
  }
}
