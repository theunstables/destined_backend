package com.loveos.api.memories.repo;

import com.loveos.api.memories.domain.AlbumMemory;
import com.loveos.api.memories.domain.AlbumMemoryId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlbumMemoryRepository extends JpaRepository<AlbumMemory, AlbumMemoryId> {
  List<AlbumMemory> findByAlbumId(UUID albumId);
  List<AlbumMemory> findByMemoryId(UUID memoryId);
  long countByAlbumId(UUID albumId);
  void deleteByAlbumIdAndMemoryId(UUID albumId, UUID memoryId);
}