package com.loveos.api.pairing.repo;

import com.loveos.api.pairing.domain.Couple;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoupleRepository extends JpaRepository<Couple, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select couple from Couple couple where couple.id = :id")
	Optional<Couple> findByIdForUpdate(@Param("id") UUID id);

	List<Couple> findByStatusAndUnpairExpiresAtLessThanEqual(
			com.loveos.api.pairing.domain.CoupleStatus status, Instant expiresAt);
}