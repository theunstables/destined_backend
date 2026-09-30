package com.loveos.api.memories.domain;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "album_memories")
@IdClass(AlbumMemoryId.class)
@Getter @Setter @NoArgsConstructor
public class AlbumMemory {
  @Id @Column(name = "album_id") private UUID albumId;
  @Id @Column(name = "memory_id") private UUID memoryId;
  @Column(nullable = false) private int position;
}