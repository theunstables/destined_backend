package com.loveos.api.plans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "couple_plans")
@Getter
@Setter
@NoArgsConstructor
public class PlansDocument {
  @Id
  @Column(name = "couple_id")
  private UUID coupleId;

  @Column(nullable = false, columnDefinition = "text")
  private String payload;

  @Version
  @Column(nullable = false)
  private Long revision;
}