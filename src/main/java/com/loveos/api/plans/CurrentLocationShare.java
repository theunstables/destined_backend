package com.loveos.api.plans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "current_location_shares")
@Getter
@Setter
@NoArgsConstructor
class CurrentLocationShare {
  @Id
  @Column(name = "user_id", columnDefinition = "uuid")
  private UUID userId;

  @Column(name = "couple_id", nullable = false, columnDefinition = "uuid")
  private UUID coupleId;

  @Column(nullable = false)
  private double latitude;

  @Column(nullable = false)
  private double longitude;

  @Column(name = "accuracy_m")
  private Double accuracyMeters;

  private Integer battery;

  private String activity;

  @Column(name = "shared_until")
  private Instant sharedUntil;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
