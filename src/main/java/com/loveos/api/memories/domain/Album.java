package com.loveos.api.memories.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "albums")
@Getter @Setter @NoArgsConstructor
public class Album {
  @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(columnDefinition = "uuid")
  private UUID id;
  @Column(name = "couple_id", nullable = false) private UUID coupleId;
  @Column(nullable = false, length = 60) private String label;
  @Column(length = 8) private String emoji;
  @Column(name = "cover_url", length = 2048) private String coverUrl;
  @Column(name = "system_key", length = 40) private String systemKey;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
  @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}