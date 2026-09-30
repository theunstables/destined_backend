package com.loveos.api.memories.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "tags")
@Getter @Setter @NoArgsConstructor
public class Tag {
  @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(columnDefinition = "uuid")
  private UUID id;
  @Column(name = "couple_id", nullable = false) private UUID coupleId;
  @Column(nullable = false, length = 40) private String label;
  @Column(nullable = false, length = 40) private String slug;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}