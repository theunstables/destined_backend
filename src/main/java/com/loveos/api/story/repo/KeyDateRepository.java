package com.loveos.api.story.repo;

import com.loveos.api.story.domain.KeyDate;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyDateRepository extends JpaRepository<KeyDate, UUID> {

  List<KeyDate> findByCoupleId(UUID coupleId);

  void deleteByCoupleId(UUID coupleId);

  List<KeyDate> findByCoupleIdAndDateBetween(UUID coupleId, LocalDate start, LocalDate end);
}