package com.loveos.api.repairsignal;

import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepairSignalService {

  private static final List<RepairSignalStatus> ACTIVE =
      List.of(RepairSignalStatus.OPEN, RepairSignalStatus.MUTUAL);

  private final CoupleAccess couples;
  private final RepairSignalRepository signals;

  public RepairSignalService(CoupleAccess couples, RepairSignalRepository signals) {
    this.couples = couples;
    this.signals = signals;
  }

  @Transactional
  public RepairSignalDtos.Signal current(UUID userId) {
    UUID coupleId = couples.requireMembership(userId).coupleId();
    RepairSignal signal = active(coupleId);
    return signal == null ? null : response(userId, signal);
  }

  @Transactional
  public RepairSignalDtos.Signal send(UUID userId) {
    CoupleAccess.Membership membership = couples.requireMembershipForUpdate(userId);
    if (membership.state() != CoupleAccess.State.CONNECTED) {
      throw new AppException(ErrorCode.FORBIDDEN, "Connect with your partner to send a signal");
    }

    RepairSignal signal = active(membership.coupleId());
    if (signal == null) {
      signal = new RepairSignal();
      signal.setCoupleId(membership.coupleId());
      signal.setSenderId(userId);
      signal.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
      signal = signals.saveAndFlush(signal);
    } else if (!signal.getSenderId().equals(userId)
        && signal.getStatus() == RepairSignalStatus.OPEN) {
      signal.setStatus(RepairSignalStatus.MUTUAL);
      signal.setPartnerSignaledAt(Instant.now());
      signal = signals.saveAndFlush(signal);
    }
    return response(userId, signal);
  }

  @Transactional
  public void cancel(UUID userId) {
    CoupleAccess.Membership membership = couples.requireMembership(userId);
    RepairSignal signal = active(membership.coupleId());
    if (signal == null) return;
    if (!signal.getSenderId().equals(userId)) {
      throw new AppException(ErrorCode.FORBIDDEN, "Only the sender can cancel this signal");
    }
    signal.setStatus(RepairSignalStatus.CANCELLED);
    signal.setCancelledAt(Instant.now());
    signals.save(signal);
  }

  private RepairSignal active(UUID coupleId) {
    RepairSignal signal = signals
        .findFirstByCoupleIdAndStatusInOrderByCreatedAtDesc(coupleId, ACTIVE).orElse(null);
    if (signal != null && !Instant.now().isBefore(signal.getExpiresAt())) {
      signal.setStatus(RepairSignalStatus.EXPIRED);
      signals.saveAndFlush(signal);
      return null;
    }
    return signal;
  }

  private static RepairSignalDtos.Signal response(UUID userId, RepairSignal signal) {
    return new RepairSignalDtos.Signal(
        signal.getId().toString(), signal.getStatus().name().toLowerCase(),
        signal.getSenderId().equals(userId), signal.getStatus() == RepairSignalStatus.MUTUAL,
        signal.getCreatedAt().toString(), text(signal.getPartnerSignaledAt()),
        signal.getExpiresAt().toString());
  }

  private static String text(Instant value) {
    return value == null ? null : value.toString();
  }
}
