package com.loveos.api.messaging.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MessagingRequests {
  private MessagingRequests() {}

  public record SendMessage(
      @NotBlank @Pattern(regexp = "text|photo|voice|video") String kind,
      @Size(max = 4000) String body,
      @Size(max = 2048) @Pattern(regexp = "https?://.+", message = "Media URL must be HTTP(S)")
      String mediaUri,
      @Min(0) @Max(3600000) Integer durationMs,
      String replyToId,
      @Size(min = 8, max = 64) String clientId) {

    @AssertTrue(message = "Text requires body; media messages require mediaUri")
    public boolean isPayloadValid() {
      if (kind == null) return true;
      return "text".equals(kind) ? body != null && !body.isBlank()
          : mediaUri != null && !mediaUri.isBlank();
    }
  }

  public record Reaction(@NotBlank @Size(max = 8) String emoji) {}
  public record ReadReceipt(@NotBlank String upToMessageId) {}
}
