package com.loveos.api.pairing;

import com.loveos.api.auth.domain.User;
import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.core.AppException;
import com.loveos.api.core.DateUtils;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.domain.Couple;
import com.loveos.api.pairing.domain.CoupleMember;
import com.loveos.api.pairing.domain.CoupleRole;
import com.loveos.api.pairing.domain.CoupleStatus;
import com.loveos.api.pairing.domain.CoverStyle;
import com.loveos.api.pairing.domain.Invite;
import com.loveos.api.pairing.domain.InviteStatus;
import com.loveos.api.pairing.dto.PairingRequests;
import com.loveos.api.pairing.dto.PairingResponses;
import com.loveos.api.pairing.repo.CoupleMemberRepository;
import com.loveos.api.pairing.repo.CoupleRepository;
import com.loveos.api.pairing.repo.InviteRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/** Turns two authenticated accounts into one shared, database-scoped space. */
@Service
public class PairingService {

  private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 6;
  private static final int UNPAIR_GRACE_DAYS = 7;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository users;
  private final CoupleRepository couples;
  private final CoupleMemberRepository members;
  private final InviteRepository invites;
  private final ApplicationEventPublisher events;

  public PairingService(
      UserRepository users,
      CoupleRepository couples,
      CoupleMemberRepository members,
      InviteRepository invites,
      ApplicationEventPublisher events) {
    this.users = users;
    this.couples = couples;
    this.members = members;
    this.invites = invites;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public PairingResponses.ProfileDto getProfile(UUID userId) {
    return profile(user(userId));
  }

  @Transactional
  public PairingResponses.ProfileDto saveProfile(UUID userId, PairingRequests.ProfileUpdate input) {
    User user = lockedUser(userId);
    user.setDisplayName(input.name().trim());
    user.setNickname(optionalText(input.nickname()));
    user.setPronouns(optionalText(input.pronouns()));
    user.setBirthday(parseBirthday(input.birthday()));
    user.setPhotoUrl(optionalText(input.photoUri()));
    return profile(user);
  }

  @Transactional
  public PairingResponses.InviteDto createInvite(UUID userId) {
    lockedUser(userId);
    CoupleMember membership = members.findByUserId(userId).orElse(null);
    Couple couple;

    if (membership == null) {
      couple = new Couple();
      couples.saveAndFlush(couple);

      membership = new CoupleMember();
      membership.setCoupleId(couple.getId());
      membership.setUserId(userId);
      membership.setRole(CoupleRole.FOUNDER);
      members.saveAndFlush(membership);
    } else {
      couple = couples.findById(membership.getCoupleId())
          .orElseThrow(() -> AppException.notFound("Couple not found"));
      if (couple.getStatus() != CoupleStatus.PENDING
          || membership.getRole() != CoupleRole.FOUNDER) {
        throw new AppException(ErrorCode.ALREADY_PAIRED, "You are already paired");
      }
      if (invites.findFirstByCoupleIdAndStatusOrderByCreatedAtDesc(
          couple.getId(), InviteStatus.REDEEMED).isPresent()) {
        throw new AppException(ErrorCode.ALREADY_PAIRED, "An invite is awaiting confirmation");
      }
    }

    Instant now = Instant.now();
    invites.cancelByCoupleIdAndStatusIn(
        couple.getId(), List.of(InviteStatus.ACTIVE), now);

    Invite invite = new Invite();
    invite.setCode(reserveCode());
    invite.setCoupleId(couple.getId());
    invite.setCreatedById(userId);
    invite.setExpiresAt(now.plus(24, ChronoUnit.HOURS));
    invites.saveAndFlush(invite);
    return new PairingResponses.InviteDto(invite.getCode(), invite.getExpiresAt().toString());
  }

  @Transactional
  public void cancelInvite(UUID userId, String rawCode) {
    lockedUser(userId);
    Invite invite = invites.findByCodeForUpdate(normalizeCode(rawCode)).orElse(null);
    if (invite == null
        || !invite.getCreatedById().equals(userId)
        || invite.getStatus() != InviteStatus.ACTIVE) {
      return;
    }

    Couple couple = couples.findById(invite.getCoupleId()).orElse(null);
    if (couple != null
        && couple.getStatus() == CoupleStatus.PENDING
        && members.countByCoupleId(couple.getId()) == 1) {
      couples.delete(couple);
      return;
    }

    invite.setStatus(InviteStatus.CANCELLED);
    invite.setCancelledAt(Instant.now());
  }

  @Transactional
  public PairingResponses.PartnerDto redeemCode(UUID userId, String rawCode) {
    lockedUser(userId);
    String code = normalizeCode(rawCode);
    Invite invite = invites.findByCodeForUpdate(code)
        .orElseThrow(PairingService::invalidCode);

    if (invite.getStatus() != InviteStatus.ACTIVE) throw invalidCode();
    if (!Instant.now().isBefore(invite.getExpiresAt())) {
      throw new AppException(ErrorCode.CODE_EXPIRED, "That invitation has expired");
    }
    if (invite.getCreatedById().equals(userId)) {
      throw new AppException(ErrorCode.CANNOT_PAIR_WITH_SELF, "That is your own code");
    }
    if (members.findByUserId(userId).isPresent()) {
      throw new AppException(ErrorCode.ALREADY_PAIRED, "You are already pairing or paired");
    }

    invite.setStatus(InviteStatus.REDEEMED);
    invite.setRedeemedById(userId);
    invite.setRedeemedAt(Instant.now());
    User founder = user(invite.getCreatedById());
    return partner(founder);
  }

  @Transactional
  public PairingResponses.PartnerDto confirmPartner(UUID userId, UUID partnerId) {
    lockedUser(userId);
    Invite invite = invites.findFirstByRedeemedByIdAndCreatedByIdAndStatusOrderByRedeemedAtDesc(
        userId, partnerId, InviteStatus.REDEEMED)
        .orElseThrow(PairingService::invalidCode);

    if (!Instant.now().isBefore(invite.getExpiresAt())) {
      throw new AppException(ErrorCode.CODE_EXPIRED, "That invitation has expired");
    }
    if (members.findByUserId(userId).isPresent()) {
      throw new AppException(ErrorCode.ALREADY_PAIRED, "You are already paired");
    }

    Couple couple = couples.findById(invite.getCoupleId())
        .orElseThrow(PairingService::invalidCode);
    if (couple.getStatus() != CoupleStatus.PENDING
        || members.countByCoupleId(couple.getId()) != 1) {
      throw invalidCode();
    }

    CoupleMember member = new CoupleMember();
    member.setCoupleId(couple.getId());
    member.setUserId(userId);
    member.setRole(CoupleRole.MEMBER);
    members.saveAndFlush(member);

    Instant now = Instant.now();
    couple.setStatus(CoupleStatus.CONNECTED);
    couple.setConnectedAt(now);
    invite.setStatus(InviteStatus.ACCEPTED);
    invites.cancelByCoupleIdAndStatusIn(
      couple.getId(), List.of(InviteStatus.ACTIVE), now);
    events.publishEvent(new CoupleConnectedEvent(couple.getId()));
    return partner(user(partnerId));
  }

  @Transactional(readOnly = true)
  public PairingResponses.SpaceDto getSpace(UUID userId) {
    CoupleMember membership = members.findByUserId(userId).orElse(null);
    if (membership != null) return memberSpace(userId, membership);

    Invite redeemed = invites.findFirstByRedeemedByIdAndStatusOrderByRedeemedAtDesc(
        userId, InviteStatus.REDEEMED).orElse(null);
    if (redeemed == null) return emptySpace();

    Couple couple = couples.findById(redeemed.getCoupleId()).orElse(null);
    if (couple == null || couple.getStatus() != CoupleStatus.PENDING) return emptySpace();
    return space(couple, "pending", partner(user(redeemed.getCreatedById())));
  }

  @Transactional
  public PairingResponses.SpaceDto updateSpace(UUID userId, PairingRequests.UpdateSpace input) {
    CoupleMember membership = members.findByUserId(userId)
        .orElseThrow(() -> new AppException(ErrorCode.NO_COUPLE, "You are not in a couple"));
    Couple couple = couples.findByIdForUpdate(membership.getCoupleId())
        .orElseThrow(() -> AppException.notFound("Couple not found"));
    requireConnected(couple);

    if (input.name() != null) couple.setName(optionalText(input.name()));
    if (input.shortName() != null) couple.setShortName(optionalText(input.shortName()));
    if (input.coverStyle() != null) {
      couple.setCoverStyle(CoverStyle.valueOf(input.coverStyle().toUpperCase(Locale.ROOT)));
    }
    return memberSpace(userId, membership);
  }

  @Transactional(readOnly = true)
  public PairingResponses.LifecycleDto getLifecycle(UUID userId) {
    return lifecycle(userId, coupleFor(userId, false));
  }

  @Transactional
  public PairingResponses.LifecycleDto pause(UUID userId) {
    Couple couple = coupleFor(userId, true);
    if (couple.getStatus() == CoupleStatus.PAUSED) return lifecycle(userId, couple);
    requireConnected(couple);
    couple.setStatus(CoupleStatus.PAUSED);
    couple.setPausedAt(Instant.now());
    return lifecycle(userId, couple);
  }

  @Transactional
  public PairingResponses.LifecycleDto reactivate(UUID userId) {
    Couple couple = coupleFor(userId, true);
    if (couple.getUnpairExpiresAt() != null) {
      throw new AppException(ErrorCode.CONFLICT, "Cancel the unpair request before reactivating");
    }
    if (couple.getStatus() == CoupleStatus.CONNECTED) return lifecycle(userId, couple);
    if (couple.getStatus() != CoupleStatus.PAUSED) {
      throw new AppException(ErrorCode.CONFLICT, "This shared space cannot be reactivated");
    }
    couple.setStatus(CoupleStatus.CONNECTED);
    couple.setPausedAt(null);
    return lifecycle(userId, couple);
  }

  @Transactional
  public PairingResponses.LifecycleDto requestUnpair(UUID userId) {
    Couple couple = coupleFor(userId, true);
    if (couple.getStatus() != CoupleStatus.CONNECTED
        && couple.getStatus() != CoupleStatus.PAUSED) {
      throw new AppException(ErrorCode.CONFLICT, "This shared space cannot be unpaired");
    }
    if (couple.getUnpairExpiresAt() == null) {
      Instant now = Instant.now();
      couple.setStatus(CoupleStatus.PAUSED);
      if (couple.getPausedAt() == null) couple.setPausedAt(now);
      couple.setUnpairRequestedById(userId);
      couple.setUnpairRequestedAt(now);
      couple.setUnpairExpiresAt(now.plus(UNPAIR_GRACE_DAYS, ChronoUnit.DAYS));
    }
    return lifecycle(userId, couple);
  }

  @Transactional
  public PairingResponses.LifecycleDto cancelUnpair(UUID userId) {
    Couple couple = coupleFor(userId, true);
    if (couple.getUnpairExpiresAt() != null) {
      couple.setUnpairRequestedById(null);
      couple.setUnpairRequestedAt(null);
      couple.setUnpairExpiresAt(null);
    }
    return lifecycle(userId, couple);
  }

  private PairingResponses.SpaceDto memberSpace(UUID userId, CoupleMember membership) {
    Couple couple = couples.findById(membership.getCoupleId())
        .orElseThrow(() -> AppException.notFound("Couple not found"));
    if (couple.getStatus() != CoupleStatus.PENDING) {
      CoupleMember other = members.findByCoupleId(couple.getId()).stream()
          .filter(candidate -> !candidate.getUserId().equals(userId))
          .findFirst()
          .orElse(null);
        return space(couple, couple.getStatus().name().toLowerCase(Locale.ROOT),
          other == null ? null : partner(user(other.getUserId())));
    }

    Invite redeemed = invites.findFirstByCoupleIdAndStatusOrderByCreatedAtDesc(
        couple.getId(), InviteStatus.REDEEMED).orElse(null);
    if (redeemed != null) {
      return space(couple, "pending", partner(user(redeemed.getRedeemedById())));
    }
    Invite active = invites.findFirstByCoupleIdAndStatusOrderByCreatedAtDesc(
      couple.getId(), InviteStatus.ACTIVE).orElse(null);
    String status = active != null && Instant.now().isBefore(active.getExpiresAt())
      ? "inviting"
      : "none";
    return space(couple, status, null);
  }

  private PairingResponses.SpaceDto space(
      Couple couple, String status, PairingResponses.PartnerDto partner) {
    return new PairingResponses.SpaceDto(
        couple.getId().toString(), couple.getName(), couple.getShortName(),
        couple.getCoverStyle().name().toLowerCase(Locale.ROOT), status, partner);
  }

  private static PairingResponses.SpaceDto emptySpace() {
    return new PairingResponses.SpaceDto(null, null, null, "dawn", "none", null);
  }

  private User lockedUser(UUID userId) {
    return users.findByIdForUpdate(userId)
        .orElseThrow(() -> AppException.unauthorized("Sign in to continue"));
  }

  private Couple coupleFor(UUID userId, boolean lock) {
    CoupleMember membership = members.findByUserId(userId)
        .orElseThrow(() -> new AppException(ErrorCode.NO_COUPLE, "You are not in a couple"));
    return (lock ? couples.findByIdForUpdate(membership.getCoupleId())
        : couples.findById(membership.getCoupleId()))
        .orElseThrow(() -> AppException.notFound("Couple not found"));
  }

  private static void requireConnected(Couple couple) {
    if (couple.getStatus() != CoupleStatus.CONNECTED) {
      throw new AppException(ErrorCode.FORBIDDEN, "Shared changes are paused");
    }
  }

  private static PairingResponses.LifecycleDto lifecycle(UUID userId, Couple couple) {
    return new PairingResponses.LifecycleDto(
        couple.getStatus().name().toLowerCase(Locale.ROOT),
        couple.getStatus() == CoupleStatus.CONNECTED,
        couple.getUnpairExpiresAt() != null,
        userId.equals(couple.getUnpairRequestedById()),
        text(couple.getPausedAt()), text(couple.getUnpairRequestedAt()),
        text(couple.getUnpairExpiresAt()));
  }

  private static String text(Instant value) {
    return value == null ? null : value.toString();
  }

  private User user(UUID userId) {
    return users.findById(userId)
        .orElseThrow(() -> AppException.unauthorized("Sign in to continue"));
  }

  private String reserveCode() {
    for (int attempt = 0; attempt < 20; attempt++) {
      StringBuilder code = new StringBuilder(CODE_LENGTH);
      for (int index = 0; index < CODE_LENGTH; index++) {
        code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
      }
      String candidate = code.toString();
      if (!invites.existsByCode(candidate)) return candidate;
    }
    throw new IllegalStateException("Could not reserve an invite code");
  }

  private static String normalizeCode(String rawCode) {
    return rawCode.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
  }

  private static LocalDate parseBirthday(String birthday) {
    if (birthday == null || birthday.isBlank()) return null;
    return DateUtils.parseCalendarDate(birthday);
  }

  private static String optionalText(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static PairingResponses.ProfileDto profile(User user) {
    return new PairingResponses.ProfileDto(
        user.getDisplayName(), user.getNickname(), user.getPronouns(),
        user.getBirthday() == null ? null : user.getBirthday().toString(), user.getPhotoUrl());
  }

  private static PairingResponses.PartnerDto partner(User user) {
    String name = optionalText(user.getNickname());
    if (name == null) name = optionalText(user.getDisplayName());
    if (name == null) name = user.getEmail().substring(0, user.getEmail().indexOf('@'));
    return new PairingResponses.PartnerDto(user.getId().toString(), name, user.getPhotoUrl());
  }

  private static AppException invalidCode() {
    return new AppException(ErrorCode.CODE_INVALID, "That code is not valid");
  }
}