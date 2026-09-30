package com.loveos.api.memories;

import java.util.List;
import java.util.UUID;

/** Stable application boundary for cross-feature Memories composition. */
public interface MemoriesAccess {
  Summary summary(UUID userId, int recentLimit);

  UUID upsertDailyQuestionMemory(
      UUID userId, UUID memoryId, String date, String prompt, String note);

  record RecentMemory(String id, String title, String date, String photoUri, String location) {}
  record Summary(long count, long places, long trips, List<RecentMemory> recent) {}
}