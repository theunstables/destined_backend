package com.loveos.api.pairing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class PairingRequests {

  private PairingRequests() {}

  public record ProfileUpdate(
      @NotBlank(message = "Name is required") @Size(max = 80) String name,
      @Size(max = 80) String nickname,
      @Size(max = 40) String pronouns,
      @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Birthday must be YYYY-MM-DD")
          String birthday,
      @Pattern(regexp = "^https?://.*", message = "Photo must be an http(s) URL")
          @Size(max = 1024) String photoUri) {}

  public record CancelInvite(@NotBlank @Size(max = 16) String code) {}

  public record RedeemCode(@NotBlank @Size(max = 16) String code) {}

  public record ConfirmPartner(@NotNull UUID partnerId) {}

  public record UpdateSpace(
      @Size(max = 255) String name,
      @Size(max = 100) String shortName,
      @Pattern(regexp = "dawn|dusk|night") String coverStyle) {}
}