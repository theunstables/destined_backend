package com.loveos.api.plans;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CurrentLocationShareRepository extends JpaRepository<CurrentLocationShare, UUID> {
  List<CurrentLocationShare> findByCoupleId(UUID coupleId);
}
