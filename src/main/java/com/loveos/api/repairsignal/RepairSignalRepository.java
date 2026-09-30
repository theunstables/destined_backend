package com.loveos.api.repairsignal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface RepairSignalRepository extends JpaRepository<RepairSignal, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RepairSignal> findFirstByCoupleIdAndStatusInOrderByCreatedAtDesc(
      UUID coupleId, List<RepairSignalStatus> statuses);
}
