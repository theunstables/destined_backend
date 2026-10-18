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
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "couple_plan_states")
@Getter
@Setter
@NoArgsConstructor
class CouplePlanState {
  @Id
  @Column(name = "couple_id", columnDefinition = "uuid")
  private UUID coupleId;

  @Column(nullable = false)
  private long version;

  @Column(name = "state", nullable = false, columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private String json;

  @Column(name = "updated_by", nullable = false, columnDefinition = "uuid")
  private UUID updatedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
