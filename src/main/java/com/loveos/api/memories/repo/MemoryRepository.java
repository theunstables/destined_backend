package com.loveos.api.memories.repo;

import com.loveos.api.memories.domain.Memory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryRepository extends JpaRepository<Memory, UUID> {
  List<Memory> findByCoupleIdAndDeletedAtIsNullOrderByDateDescIdDesc(UUID coupleId);
  Optional<Memory> findByIdAndCoupleIdAndDeletedAtIsNull(UUID id, UUID coupleId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select memory from Memory memory where memory.id = :id and memory.coupleId = :coupleId and memory.deletedAt is null")
  Optional<Memory> findActiveForUpdate(@Param("id") UUID id, @Param("coupleId") UUID coupleId);
}