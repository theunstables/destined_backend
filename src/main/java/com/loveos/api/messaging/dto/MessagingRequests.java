package com.loveos.api.messaging.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;
import org.springframework.web.util.UriComponentsBuilder;

public final class MessagingRequests {
  private MessagingRequests() {}

  public record SendMessage(
      @NotBlank @Pattern(regexp = "text|photo|voice|video|note|location") String kind,
      @Size(max = 4000) String body,
      @Size(max = 2048) @Pattern(regexp = "https?://.+", message = "Media URL must be HTTP(S)")
      String mediaUri,
      @Min(0) @Max(3600000) Integer durationMs,
      String replyToId,
      @Size(min = 8, max = 64) String clientId) {

    @AssertTrue(message = "Text and notes require body; media require a URL; locations require valid coordinates")
    public boolean isPayloadValid() {
      if (kind == null) return true;
      if ("text".equals(kind) || "note".equals(kind)) return body != null && !body.isBlank();
      if ("location".equals(kind)) {
        try {
          URI uri = URI.create(mediaUri);
          if (!"https".equals(uri.getScheme()) || !"www.openstreetmap.org".equals(uri.getHost())
              || uri.getRawUserInfo() != null || uri.getPort() != -1 || !"/".equals(uri.getPath())) return false;
          var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
          if (!query.keySet().equals(Set.of("mlat", "mlon"))
              || query.get("mlat").size() != 1 || query.get("mlon").size() != 1) return false;
          double latitude = Double.parseDouble(query.getFirst("mlat"));
          double longitude = Double.parseDouble(query.getFirst("mlon"));
          return Double.isFinite(latitude) && Double.isFinite(longitude)
              && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
        } catch (RuntimeException invalid) {
          return false;
        }
      }
      return mediaUri != null && !mediaUri.isBlank();
    }
  }

  public record Reaction(@NotBlank @Size(max = 8) String emoji) {}
  public record ReadReceipt(@NotBlank String upToMessageId) {}
}
