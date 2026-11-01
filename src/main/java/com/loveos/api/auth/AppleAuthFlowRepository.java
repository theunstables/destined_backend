package com.loveos.api.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AppleAuthFlowRepository extends JpaRepository<AppleAuthFlow, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AppleAuthFlow> findByStateHash(String stateHash);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AppleAuthFlow> findByTicketHash(String ticketHash);
  void deleteByExpiresAtBefore(Instant before);
}