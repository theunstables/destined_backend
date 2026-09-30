package com.loveos.api.repairsignal;

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
@Table(name = "repair_signals")
@Getter
@Setter
@NoArgsConstructor
class RepairSignal {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "couple_id", nullable = false, columnDefinition = "uuid")
  private UUID coupleId;

  @Column(name = "sender_id", nullable = false, columnDefinition = "uuid")
  private UUID senderId;

  @Column(name = "partner_signaled_at")
  private Instant partnerSignaledAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RepairSignalStatus status = RepairSignalStatus.OPEN;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;
}
