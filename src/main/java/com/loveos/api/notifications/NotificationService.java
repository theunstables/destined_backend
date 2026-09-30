package com.loveos.api.notifications;

import com.loveos.api.core.AppException;
import com.loveos.api.core.DateUtils;
import com.loveos.api.core.ErrorCode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final NotificationPreferenceRepository preferences;
  private final DeviceTokenRepository devices;

  NotificationService(
      NotificationPreferenceRepository preferences, DeviceTokenRepository devices) {
    this.preferences = preferences;
    this.devices = devices;
  }

  @Transactional
  public NotificationDtos.Preferences getPreferences(UUID userId) {
    return response(preferences.findById(userId).orElseGet(() -> createDefaults(userId)));
  }

  @Transactional
  public NotificationDtos.Preferences updatePreferences(
      UUID userId, NotificationDtos.UpdatePreferences input) {
    LocalTime start = time(input.quietStart());
    LocalTime end = time(input.quietEnd());
    if ((start == null) != (end == null)) {
      throw validation("quietStart and quietEnd must both be set or both be empty");
    }
    String timezone = DateUtils.parseZoneId(input.timezone()).getId();

    NotificationPreference preference = preferences.findById(userId)
        .orElseGet(() -> createDefaults(userId));
    preference.setEnabled(input.enabled());
    preference.setMessages(input.messages());
    preference.setOccasions(input.occasions());
    preference.setMemories(input.memories());
    preference.setQuietStart(start);
    preference.setQuietEnd(end);
    preference.setTimezone(timezone);
    return response(preference);
  }

  @Transactional
  public NotificationDtos.Device register(UUID userId, NotificationDtos.RegisterDevice input) {
    String token = input.token().trim();
    DeviceToken device = devices.findByToken(token).orElseGet(DeviceToken::new);
    device.setUserId(userId);
    device.setToken(token);
    device.setPlatform(input.platform());
    device.setActive(true);
    devices.saveAndFlush(device);
    return new NotificationDtos.Device(
        device.getId().toString(), device.getPlatform().name().toLowerCase(), true);
  }

  @Transactional
  public void unregister(UUID userId, NotificationDtos.UnregisterDevice input) {
    devices.findByToken(input.token().trim())
        .filter(device -> device.getUserId().equals(userId))
        .ifPresent(device -> device.setActive(false));
  }

  private NotificationPreference createDefaults(UUID userId) {
    NotificationPreference preference = new NotificationPreference();
    preference.setUserId(userId);
    return preferences.save(preference);
  }

  private static NotificationDtos.Preferences response(NotificationPreference preference) {
    return new NotificationDtos.Preferences(
        preference.isEnabled(), preference.isMessages(), preference.isOccasions(),
        preference.isMemories(), text(preference.getQuietStart()),
        text(preference.getQuietEnd()), preference.getTimezone());
  }

  private static LocalTime time(String value) {
    return DateUtils.parseLocalTime(value);
  }

  private static String text(LocalTime value) {
    return DateUtils.format(value);
  }

  static boolean isQuietAt(NotificationPreference preference, Instant when) {
    return DateUtils.isInQuietWindow(
        when, DateUtils.parseZoneId(preference.getTimezone()),
        preference.getQuietStart(), preference.getQuietEnd());
  }

  private static AppException validation(String message) {
    return new AppException(ErrorCode.VALIDATION_ERROR, message);
  }
}
