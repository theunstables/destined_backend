package com.loveos.api.notifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class NotificationDtos {

  private NotificationDtos() {}

  public record Preferences(
      boolean enabled,
      boolean messages,
      boolean occasions,
      boolean memories,
      String quietStart,
      String quietEnd,
      String timezone) {}

  public record UpdatePreferences(
      boolean enabled,
      boolean messages,
      boolean occasions,
      boolean memories,
      String quietStart,
      String quietEnd,
    @NotBlank @Size(max = 64) String timezone) {}

  public record RegisterDevice(
      @NotBlank @Size(max = 2048) String token,
      @NotNull DevicePlatform platform) {}

  public record UnregisterDevice(@NotBlank @Size(max = 2048) String token) {}

  public record Device(String id, String platform, boolean active) {}
}
