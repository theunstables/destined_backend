package com.loveos.api.memories.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "memories")
@Getter @Setter @NoArgsConstructor
public class Memory {
  @Id @GeneratedValue(strategy = GenerationType.UUID) @Column(columnDefinition = "uuid")
  private UUID id;
  @Column(name = "couple_id", nullable = false) private UUID coupleId;
  @Column(name = "author_id", nullable = false) private UUID authorId;
  @Column(nullable = false, length = 200) private String title;
  @Column(nullable = false) private LocalDate date;
  @Column(length = 500) private String caption;
  @Column(length = 160) private String location;
  @Column(length = 4000) private String note;
  @Column(nullable = false) private boolean favorite;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
  @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Column(name = "deleted_at") private Instant deletedAt;
}