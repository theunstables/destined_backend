package com.loveos.api.memories.repo;

import com.loveos.api.memories.domain.Album;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlbumRepository extends JpaRepository<Album, UUID> {
  List<Album> findByCoupleIdOrderByCreatedAtAsc(UUID coupleId);
  Optional<Album> findByIdAndCoupleId(UUID id, UUID coupleId);
  Optional<Album> findByCoupleIdAndSystemKey(UUID coupleId, String systemKey);
  boolean existsByCoupleIdAndLabelIgnoreCase(UUID coupleId, String label);
  boolean existsByCoupleIdAndSystemKey(UUID coupleId, String systemKey);
}