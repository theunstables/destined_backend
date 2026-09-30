package com.loveos.api.pairing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "invites")
@Getter
@Setter
@NoArgsConstructor
public class Invite {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(nullable = false, unique = true, length = 6)
  private String code;

  @Column(name = "couple_id", nullable = false)
  private UUID coupleId;

  @Column(name = "created_by_id", nullable = false)
  private UUID createdById;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InviteStatus status = InviteStatus.ACTIVE;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "redeemed_by_id")
  private UUID redeemedById;

  @Column(name = "redeemed_at")
  private Instant redeemedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Invite invite)) return false;
    return id != null && id.equals(invite.id);
  }

  @Override
  public int hashCode() {
    return Invite.class.hashCode();
  }
}