package com.loveos.api.media;

import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MediaService {

  private final CoupleAccess coupleAccess;
  private final StorageProvider storage;
  private final MediaProperties properties;
  private final String publicApiUrl;

  public MediaService(
      CoupleAccess coupleAccess,
      StorageProvider storage,
      MediaProperties properties,
      com.loveos.api.config.AppProperties appProperties) {
    this.coupleAccess = coupleAccess;
    this.storage = storage;
    this.properties = properties;
    this.publicApiUrl = appProperties.publicApiUrl().replaceAll("/+$", "");
  }

  public UploadedMedia upload(UUID userId, UploadInput file) {
    coupleAccess.requireMembership(userId);
    if (file.bytes() == 0) throw new AppException(ErrorCode.INVALID_REQUEST, "No file uploaded");
    if (file.bytes() > properties.maxBytes()) {
      throw new AppException(ErrorCode.PAYLOAD_TOO_LARGE,
          "Files must be under " + properties.maxBytes() / 1024 / 1024 + " MB");
    }

    try (var input = new BufferedInputStream(file.content())) {
      input.mark(32);
      byte[] header = input.readNBytes(16);
      input.reset();
      String contentType = normalized(file.contentType());
      String key = UUID.randomUUID() + MediaTypePolicy.extension(contentType, header);
      storage.store(key, input, file.bytes(), contentType);
      String fallback = publicApiUrl + "/media/" + key;
      return new UploadedMedia(storage.publicUrl(key, fallback), contentType, file.bytes());
    } catch (IOException exception) {
      throw new AppException(ErrorCode.INTERNAL_ERROR, "Media could not be stored");
    }
  }

  public StoredMedia load(String key) {
    if (!key.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp|heic|mp4|mov|m4a|mp3|webm|ogg)")) {
      throw AppException.notFound("Media not found");
    }
    try {
      var object = storage.load(key).orElseThrow(() -> AppException.notFound("Media not found"));
      return new StoredMedia(object, contentType(key));
    } catch (IOException exception) {
      throw new AppException(ErrorCode.INTERNAL_ERROR, "Media could not be read");
    }
  }

  private static String normalized(String value) {
    return value == null ? "" : value.toLowerCase().split(";", 2)[0].trim();
  }

  private static String contentType(String key) {
    if (key.endsWith(".jpg")) return "image/jpeg";
    if (key.endsWith(".png")) return "image/png";
    if (key.endsWith(".webp")) return "image/webp";
    if (key.endsWith(".heic")) return "image/heic";
    if (key.endsWith(".mp4")) return "video/mp4";
    if (key.endsWith(".mov")) return "video/quicktime";
    if (key.endsWith(".m4a")) return "audio/mp4";
    if (key.endsWith(".mp3")) return "audio/mpeg";
    if (key.endsWith(".webm")) return "audio/webm";
    return "audio/ogg";
  }

  public record UploadedMedia(String url, String contentType, long bytes) {}
  public record UploadInput(InputStream content, String contentType, long bytes) {}
  public record StoredMedia(StorageProvider.StoredObject object, String contentType) {}
}