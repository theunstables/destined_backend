package com.loveos.api.memories.repo;

import com.loveos.api.memories.domain.MemoryPhoto;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryPhotoRepository extends JpaRepository<MemoryPhoto, UUID> {
  List<MemoryPhoto> findByMemoryIdOrderByPositionAsc(UUID memoryId);
  List<MemoryPhoto> findByMemoryIdInOrderByMemoryIdAscPositionAsc(List<UUID> memoryIds);
  void deleteByMemoryId(UUID memoryId);
}