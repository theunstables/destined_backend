package com.loveos.api.calendar.dto;

public final class CalendarResponses {

  private CalendarResponses() {}

  public record EventDto(
      String id,
      String title,
      String date,
      String startsAt,
      String endsAt,
      String location,
      String notes,
      String kind,
      boolean repeatsAnnually,
      Integer reminderMinutesBefore,
      String createdAt) {}

  public record ComingUpDto(
      String key,
      String label,
      String detail,
      long days,
      String date,
      String kind,
      String source) {}
}