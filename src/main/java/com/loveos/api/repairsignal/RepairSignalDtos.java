package com.loveos.api.repairsignal;

public final class RepairSignalDtos {

  private RepairSignalDtos() {}

  public record Signal(
      String id,
      String status,
      boolean sentByMe,
      boolean mutual,
      String createdAt,
      String partnerSignaledAt,
      String expiresAt) {}
}
