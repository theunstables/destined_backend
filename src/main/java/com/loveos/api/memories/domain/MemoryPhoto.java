package com.loveos.api.memories.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "memory_photos")
@Getter @Setter @NoArgsConstructor
public class MemoryPhoto {
  @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(columnDefinition = "uuid")
  private UUID id;
  @Column(name = "memory_id", nullable = false) private UUID memoryId;
  @Column(nullable = false, length = 2048) private String url;
  private Integer width;
  private Integer height;
  @Column(nullable = false) private int position;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}