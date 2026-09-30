package com.loveos.api.memories.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class MemoryRequests {
  public static final String DATE = "^\\d{4}-\\d{2}-\\d{2}$";
  public static final String URL = "^https?://.+";
  private MemoryRequests() {}

  public record CreateMemory(
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String date,
      @Size(max = 500) String caption,
      @Size(max = 160) String location,
      @Size(max = 4000) String note,
      @Pattern(regexp = URL, message = "Photo must be an http(s) URL") @Size(max = 2048) String photoUri,
      @Size(max = 20) List<@Pattern(regexp = URL) @Size(max = 2048) String> photos,
      @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
      Boolean favorite,
      @Size(max = 10) List<String> albumIds) {}

  public record UpdateMemory(
      @Size(min = 1, max = 200) String title,
      @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String date,
      @Size(max = 500) String caption,
      @Size(max = 160) String location,
      @Size(max = 4000) String note,
      @Pattern(regexp = URL, message = "Photo must be an http(s) URL") @Size(max = 2048) String photoUri,
      @Size(max = 20) List<@Pattern(regexp = URL) @Size(max = 2048) String> photos,
      @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
      Boolean favorite) {}

    public record UpsertPrivateNote(
            @NotBlank @Size(max = 4000) String note) {}

  public record CreateAlbum(
      @NotBlank @Size(max = 60) String label,
      @Size(max = 8) String emoji,
      @Pattern(regexp = URL) @Size(max = 2048) String coverUrl) {}
}