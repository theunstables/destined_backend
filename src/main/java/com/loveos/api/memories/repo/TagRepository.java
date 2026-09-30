package com.loveos.api.memories.repo;

import com.loveos.api.memories.domain.Tag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, UUID> {
  Optional<Tag> findByCoupleIdAndSlug(UUID coupleId, String slug);
  List<Tag> findByIdIn(List<UUID> ids);
}