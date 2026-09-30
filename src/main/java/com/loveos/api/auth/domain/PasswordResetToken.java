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
 * The single-use token behind a password-reset link.
 *
 * <p>Structurally identical to {@link EmailVerificationToken} — see the note
 * there on why they are two tables rather than one.
 *
 * <p>SHORT-LIVED BY DESIGN. Thirty minutes, against twenty-four hours for
 * verification. This token can take over an account, so the window in which a
 * forwarded email or a synced mailbox on an old device is dangerous should be
 * about as long as it takes to read the message and act on it.
 *
 * <p>Consuming one revokes every refresh session the user has. If the reset was
 * requested because the account was compromised, leaving the attacker's session
 * alive would defeat the entire exercise.
 */
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = @Index(name = "idx_reset_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
  private UUID userId;

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
