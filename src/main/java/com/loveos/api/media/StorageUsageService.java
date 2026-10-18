package com.loveos.api.media;

import com.loveos.api.pairing.CoupleAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageUsageService {
  private final CoupleAccess couples;
  private final MediaObjectRepository objects;

  public StorageUsageService(CoupleAccess couples, MediaObjectRepository objects) {
    this.couples = couples;
    this.objects = objects;
  }

  @Transactional(readOnly = true)
  public Usage get(UUID userId) {
    UUID coupleId = couples.requireMembership(userId).coupleId();
    List<MediaObject> rows = objects.findByCoupleId(coupleId);
    long photos = bytes(rows, "image/");
    long videos = bytes(rows, "video/");
    long audio = bytes(rows, "audio/");
    return new Usage(photos + videos + audio, photos, videos, audio);
  }

  private static long bytes(List<MediaObject> rows, String prefix) {
    return rows.stream().filter(row -> row.getContentType().startsWith(prefix))
        .mapToLong(MediaObject::getBytes).sum();
  }

  public record Usage(long totalBytes, long photoBytes, long videoBytes, long audioBytes) {}
}
