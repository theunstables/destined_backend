package com.loveos.api.pairing;

import com.loveos.api.pairing.domain.CoupleStatus;
import com.loveos.api.pairing.repo.CoupleRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Archives expired unpair requests without deleting either partner's shared history. */
@Component
class UnpairExpiryJob {

  private final CoupleRepository couples;

  UnpairExpiryJob(CoupleRepository couples) {
    this.couples = couples;
  }

  @Scheduled(fixedDelayString = "${loveos.lifecycle.unpair-scan-delay:PT1M}")
  @Transactional
  void archiveExpiredRequests() {
    for (var couple : couples.findByStatusAndUnpairExpiresAtLessThanEqual(
        CoupleStatus.PAUSED, Instant.now())) {
      couple.setStatus(CoupleStatus.ARCHIVED);
    }
  }
}