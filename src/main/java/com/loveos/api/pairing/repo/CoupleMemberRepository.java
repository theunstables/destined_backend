package com.loveos.api.pairing.repo;

import com.loveos.api.pairing.domain.CoupleMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoupleMemberRepository extends JpaRepository<CoupleMember, UUID> {

  Optional<CoupleMember> findByUserId(UUID userId);

  List<CoupleMember> findByCoupleId(UUID coupleId);

  long countByCoupleId(UUID coupleId);
}