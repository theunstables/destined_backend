package com.loveos.api.account;

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
@Table(name = "account_deletion_requests")
@Getter
@Setter
@NoArgsConstructor
class AccountDeletionRequest {

  @Id
  @Column(name = "user_id", columnDefinition = "uuid")
  private UUID userId;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "execute_after", nullable = false)
  private Instant executeAfter;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "executed_at")
  private Instant executedAt;
}
