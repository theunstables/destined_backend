package com.loveos.api.memories.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "memory_tags")
@IdClass(MemoryTagId.class)
@Getter @Setter @NoArgsConstructor
public class MemoryTag {
  @Id @Column(name = "memory_id") private UUID memoryId;
  @Id @Column(name = "tag_id") private UUID tagId;
}