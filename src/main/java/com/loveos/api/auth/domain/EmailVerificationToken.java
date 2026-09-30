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
 * The single-use token behind the "confirm your email" link.
 *
 * <p>Its twin, {@link PasswordResetToken}, has the same columns. They are kept
 * as separate tables because the Prisma schema in {@code ../ziyu_backend} does —
 * merging them would fork the schema during the migration — and because the two
 * genuinely differ in lifetime (24 hours against 30 minutes) and in what
 * consuming one is allowed to do.
 *
 * <p>ONLY THE DIGEST IS STORED. The raw token exists in the email and nowhere
 * else, so read access to this table does not let anyone verify an address or
 * reset a password.
 *
 * <p>CONSUMPTION IS A TIMESTAMP, NOT A DELETE. Keeping the used row lets a second
 * click on the same link be answered with "already verified" rather than
 * "invalid link" — the difference between a reassuring message and a worrying
 * one, for something users click twice all the time.
 */
@Entity
@Table(
    name = "email_verification_tokens",
    indexes = @Index(name = "idx_verif_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

  /** SHA-256 of the emailed token. The raw value exists only in the email. */
  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public boolean isUsable(Instant now) {
    return consumedAt == null && expiresAt.isAfter(now);
  }
}
