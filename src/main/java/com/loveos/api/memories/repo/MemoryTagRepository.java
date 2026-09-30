package com.loveos.api.memories.repo;

import com.loveos.api.memories.domain.MemoryTag;
import com.loveos.api.memories.domain.MemoryTagId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryTagRepository extends JpaRepository<MemoryTag, MemoryTagId> {
  List<MemoryTag> findByMemoryId(UUID memoryId);
  List<MemoryTag> findByMemoryIdIn(List<UUID> memoryIds);
  void deleteByMemoryId(UUID memoryId);
}