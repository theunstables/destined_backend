package com.loveos.api.preferences;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferencesService {
  private static final int MAX_BYTES = 32 * 1024;
  private static final Set<String> ALLOWED_KEYS = Set.of(
      "textScale", "reduceMotion", "haptics", "highContrast",
      "notificationsEnabled", "notifyMessages", "notifyVoiceNotes", "notifyNewMemories",
      "notifyOnThisDay", "notifyOccasions", "notifyPartnerActivity", "quietHoursEnabled",
      "quietHoursFrom", "quietHoursTo", "notificationSound", "notificationVibration",
      "readReceipts", "typingIndicator", "autoSaveMedia", "voiceNoteAutoPlay",
      "messageTextScale", "onThisDayEnabled", "onThisDayTime", "defaultAlbumKey",
      "autoAddChatPhotos", "memoryReminders", "anniversaryLead", "birthdayLead",
      "occasionLead", "homeCards", "language", "dateFormat", "clock", "weekStart",
      "appLock", "hideNotificationContent", "showOnlineStatus", "screenshotAlerts",
      "autoDownload", "uploadQuality");

  private final UserPreferencesRepository repository;
  private final ObjectMapper mapper;

  public PreferencesService(UserPreferencesRepository repository, ObjectMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public JsonNode get(UUID userId) {
    return repository.findById(userId).map(row -> parse(row.getJson())).orElse(null);
  }

  @Transactional
  public JsonNode replace(UUID userId, JsonNode preferences) {
    validate(preferences);
    UserPreferences row = repository.findById(userId).orElseGet(UserPreferences::new);
    row.setUserId(userId);
    row.setVersion(row.getVersion() + 1);
    row.setJson(write(preferences));
    repository.saveAndFlush(row);
    return preferences.deepCopy();
  }

  private void validate(JsonNode preferences) {
    if (preferences == null || !preferences.isObject()) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Preferences must be an object");
    }
    preferences.propertyNames().forEach(key -> {
      if (!ALLOWED_KEYS.contains(key)) {
        throw new AppException(ErrorCode.VALIDATION_ERROR, "Unsupported preference: " + key);
      }
    });
    String json = write(preferences);
    if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new AppException(ErrorCode.PAYLOAD_TOO_LARGE, "Preferences exceed 32 KB");
    }
  }

  private JsonNode parse(String json) {
    try {
      return mapper.readTree(json);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored preferences are invalid JSON", exception);
    }
  }

  private String write(JsonNode value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Preferences cannot be serialized", exception);
    }
  }
}
