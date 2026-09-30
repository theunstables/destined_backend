package com.loveos.api.memories.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "memory_private_notes")
@IdClass(MemoryPrivateNoteId.class)
@Getter @Setter @NoArgsConstructor
public class MemoryPrivateNote {
  @Id @Column(name = "memory_id", nullable = false) private UUID memoryId;
  @Id @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(nullable = false, length = 4000) private String note;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
  @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
