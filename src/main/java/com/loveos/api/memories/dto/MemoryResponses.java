package com.loveos.api.memories.dto;

import java.util.List;

public final class MemoryResponses {
  private MemoryResponses() {}

  public record MemoryDto(
      String id, String title, String date, String caption, String location,
      String photoUri, List<String> photos, String note, List<String> tags,
      boolean favorite, String addedBy, String createdAt, String updatedAt,
      String myPrivateNote, String partnerPrivateNote, boolean reciprocalNotesRevealed) {}

  public record AlbumDto(
      String id, String key, String label, String emoji, String coverUri, long count) {}

  public record MemoryPage(List<MemoryDto> items, String nextCursor) {}
}