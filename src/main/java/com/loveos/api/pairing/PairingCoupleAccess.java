package com.loveos.api.pairing;

import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.pairing.domain.Couple;
import com.loveos.api.pairing.domain.CoupleMember;
import com.loveos.api.pairing.domain.CoupleStatus;
import com.loveos.api.pairing.repo.CoupleMemberRepository;
import com.loveos.api.pairing.repo.CoupleRepository;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class PairingCoupleAccess implements CoupleAccess {

  private final CoupleMemberRepository members;
  private final CoupleRepository couples;
  private final UserRepository users;

  PairingCoupleAccess(
      CoupleMemberRepository members, CoupleRepository couples, UserRepository users) {
    this.members = members;
    this.couples = couples;
    this.users = users;
  }

  @Override
  public Membership requireMembership(UUID userId) {
    CoupleMember member = member(userId);
    return context(member, couples.findById(member.getCoupleId())
        .orElseThrow(() -> AppException.notFound("Couple not found")));
  }

  @Override
  public Membership requireMembershipForUpdate(UUID userId) {
    CoupleMember member = member(userId);
    Couple couple = couples.findByIdForUpdate(member.getCoupleId())
        .orElseThrow(() -> AppException.notFound("Couple not found"));
    if (couple.getStatus() == CoupleStatus.PAUSED
      || couple.getStatus() == CoupleStatus.ARCHIVED) {
      throw new AppException(ErrorCode.FORBIDDEN, "Shared changes are paused");
    }
    return context(member, couple);
  }

  @Override
  public List<Member> members(UUID userId) {
    Membership membership = requireMembership(userId);
    return members.findByCoupleId(membership.coupleId()).stream()
        .map(member -> {
          var user = users.findById(member.getUserId())
              .orElseThrow(() -> AppException.notFound("Member not found"));
          return new Member(user.getId(), Role.valueOf(member.getRole().name()),
              user.getDisplayName(), user.getNickname());
        })
        .toList();
  }

  @Override
  public Space space(UUID userId) {
    CoupleMember member = member(userId);
    Couple couple = couples.findById(member.getCoupleId())
        .orElseThrow(() -> AppException.notFound("Couple not found"));
    return new Space(couple.getName(), couple.getShortName(),
        couple.getCoverStyle().name().toLowerCase(java.util.Locale.ROOT));
  }

  private CoupleMember member(UUID userId) {
    return members.findByUserId(userId)
        .orElseThrow(() -> new AppException(ErrorCode.NO_COUPLE, "Join a couple to continue"));
  }

  private static Membership context(CoupleMember member, Couple couple) {
    return new Membership(
        couple.getId(), Role.valueOf(member.getRole().name()),
        couple.getStatus() == CoupleStatus.CONNECTED,
        CoupleAccess.State.valueOf(couple.getStatus().name()));
  }
}