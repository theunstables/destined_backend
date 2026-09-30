package com.loveos.api.pairing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class PairingResponses {

  private PairingResponses() {}

  public record ProfileDto(
      String name,
      @JsonInclude(JsonInclude.Include.NON_NULL) String nickname,
      @JsonInclude(JsonInclude.Include.NON_NULL) String pronouns,
      @JsonInclude(JsonInclude.Include.NON_NULL) String birthday,
      @JsonInclude(JsonInclude.Include.NON_NULL) String photoUri) {}

  public record PartnerDto(
      String id,
      String name,
      @JsonInclude(JsonInclude.Include.NON_NULL) String photoUri) {}

  public record InviteDto(String code, String expiresAt) {}

  public record SpaceDto(
      @JsonInclude(JsonInclude.Include.NON_NULL) String coupleId,
      @JsonInclude(JsonInclude.Include.NON_NULL) String name,
      @JsonInclude(JsonInclude.Include.NON_NULL) String shortName,
      String coverStyle,
      String status,
      @JsonInclude(JsonInclude.Include.NON_NULL) PartnerDto partner) {}

  public record LifecycleDto(
      String status,
      boolean sharedWritesAllowed,
      boolean unpairPending,
      boolean unpairRequestedByMe,
      @JsonInclude(JsonInclude.Include.NON_NULL) String pausedAt,
      @JsonInclude(JsonInclude.Include.NON_NULL) String unpairRequestedAt,
      @JsonInclude(JsonInclude.Include.NON_NULL) String unpairExpiresAt) {}
}