package com.loveos.api.account;

import java.util.UUID;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountDeletionRequestRepository
    extends JpaRepository<AccountDeletionRequest, UUID> {
  List<AccountDeletionRequest> findByCancelledAtIsNullAndExecutedAtIsNullAndExecuteAfterLessThanEqual(
      Instant now);
}
