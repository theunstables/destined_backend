package com.loveos.api.story.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StoryRequests {

  private static final String DATE = "^\\d{4}-\\d{2}-\\d{2}$";

  private StoryRequests() {}

  public record StoryDate(
      @NotBlank @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String value,
      @NotBlank @Pattern(regexp = "exact|monthYear|yearOnly") String precision) {}

  public record Moment(
      @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String date,
      @Size(max = 120) String location,
      @Size(max = 2000) String note,
      @Pattern(regexp = "^https?://.+", message = "Photo must be an http(s) URL")
          @Size(max = 2048) String photoUri) {}

  public record KeyDates(
      @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String anniversary,
      @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String yourBirthday,
      @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String partnerBirthday,
      @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String firstDate,
      @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String firstMeeting) {}

  public record SaveStory(
      @Valid StoryDate met,
      @Valid Moment firstDate,
      @Valid Moment becameUs,
      @Valid Moment firstMemory,
      @Valid KeyDates keyDates) {}
}