package com.loveos.api.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A person. Not a couple — see the note on scoping in the API docs.
 *
 * <p>{@code passwordHash} IS NULLABLE, and that is not an oversight. An account
 * created through Google or Apple has no password at all, and storing a dummy
 * value would make "has this user set a password?" unanswerable. Null means
 * "this account cannot be signed into with a password", which the login path
 * checks explicitly.
 *
 * <p>Table and column names mirror the Prisma schema in {@code ../ziyu_backend}
 * exactly, so both implementations can point at the same database during the
 * migration. Renaming anything here silently forks the schema.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  /**
   * Stored lower-cased and trimmed by the service.
   *
   * <p>The unique constraint is the real defence against duplicate sign-ups: a
   * "does this email exist?" check followed by an insert is a race that two
   * simultaneous requests will win together. The check exists for the friendly
   * error; the constraint exists for correctness.
   *
   * <p>254 is the maximum length of an email address under RFC 5321. Lengths are
  * declared throughout this class rather than left to Hibernate's default of
  * 255, so that the entity and Flyway migration state the same thing and
  * neither has to be read to understand the other.
   */
  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified = false;

  /** Null for OAuth-only accounts. See the class note. */
  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "display_name", length = 120)
  private String displayName;

  @Column(length = 120)
  private String nickname;

  @Column(length = 60)
  private String pronouns;

  /** A calendar day, never an instant — a birthday has no timezone. */
  private LocalDate birthday;

  @Column(name = "photo_url", length = 1024)
  private String photoUrl;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * Identity is the primary key and nothing else.
   *
   * <p>Lombok's {@code @Data} is deliberately not used on entities: it generates
   * {@code equals} over every field, which for a JPA entity means touching lazy
   * associations and comparing mutable state. Two loads of the same row would
   * compare unequal after one is edited, quietly breaking any {@code Set} or map
   * they are placed in.
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof User user)) return false;
    return id != null && id.equals(user.id);
  }

  @Override
  public int hashCode() {
    // Constant, not id-based: the hash must not change when a transient entity
    // is persisted and acquires an id.
    return User.class.hashCode();
  }
}
