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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "couples")
@Getter
@Setter
@NoArgsConstructor
public class Couple {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(length = 255)
  private String name;

  @Column(name = "short_name", length = 100)
  private String shortName;

  @Enumerated(EnumType.STRING)
  @Column(name = "cover_style", nullable = false, length = 20)
  private CoverStyle coverStyle = CoverStyle.DAWN;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CoupleStatus status = CoupleStatus.PENDING;

  @Column(name = "connected_at")
  private Instant connectedAt;

  @Column(name = "paused_at")
  private Instant pausedAt;

  @Column(name = "unpair_requested_by_id")
  private UUID unpairRequestedById;

  @Column(name = "unpair_requested_at")
  private Instant unpairRequestedAt;

  @Column(name = "unpair_expires_at")
  private Instant unpairExpiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Couple couple)) return false;
    return id != null && id.equals(couple.id);
  }

  @Override
  public int hashCode() {
    return Couple.class.hashCode();
  }
}