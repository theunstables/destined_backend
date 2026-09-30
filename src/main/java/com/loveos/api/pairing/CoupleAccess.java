package com.loveos.api.pairing;

import java.util.UUID;
import java.util.List;

/** Stable application boundary through which other features obtain couple scope. */
public interface CoupleAccess {

  Membership requireMembership(UUID userId);

  Membership requireMembershipForUpdate(UUID userId);

  /** Member display data for couple-scoped projections such as birthday labels. */
  List<Member> members(UUID userId);

  Space space(UUID userId);

  enum Role {
    FOUNDER,
    MEMBER
  }

  enum State {
    PENDING,
    CONNECTED,
    PAUSED,
    ARCHIVED
  }

  record Membership(UUID coupleId, Role role, boolean connected, State state) {
    public Membership(UUID coupleId, Role role, boolean connected) {
      this(coupleId, role, connected, connected ? State.CONNECTED : State.PENDING);
    }
  }

  record Member(UUID userId, Role role, String displayName, String nickname) {}

  record Space(String name, String shortName, String coverStyle) {}
}