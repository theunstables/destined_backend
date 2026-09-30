package com.loveos.api.memories.repo;

import com.loveos.api.memories.domain.MemoryPrivateNote;
import com.loveos.api.memories.domain.MemoryPrivateNoteId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryPrivateNoteRepository
    extends JpaRepository<MemoryPrivateNote, MemoryPrivateNoteId> {
  List<MemoryPrivateNote> findByMemoryId(UUID memoryId);
  Optional<MemoryPrivateNote> findByMemoryIdAndUserId(UUID memoryId, UUID userId);
  void deleteByMemoryIdAndUserId(UUID memoryId, UUID userId);
}
