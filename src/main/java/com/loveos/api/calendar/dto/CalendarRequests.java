package com.loveos.api.calendar.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CalendarRequests {

  public static final String DATE = "^\\d{4}-\\d{2}-\\d{2}$";
  public static final String KIND = "anniversary|birthday|dateNight|trip|reminder|custom";

  private CalendarRequests() {}

  public record CreateEvent(
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String date,
      String startsAt,
      String endsAt,
      @Size(max = 160) String location,
      @Size(max = 2000) String notes,
      @Pattern(regexp = KIND) String kind,
      Boolean repeatsAnnually,
      @Min(0) @Max(43200) Integer reminderMinutesBefore) {}

  public record UpdateEvent(
      @Size(min = 1, max = 160) String title,
      @Pattern(regexp = DATE, message = "Date must be YYYY-MM-DD") String date,
      String startsAt,
      String endsAt,
      @Size(max = 160) String location,
      @Size(max = 2000) String notes,
      @Pattern(regexp = KIND) String kind,
      Boolean repeatsAnnually,
      @Min(0) @Max(43200) Integer reminderMinutesBefore) {}
}