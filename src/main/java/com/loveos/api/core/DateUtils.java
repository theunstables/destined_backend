package com.loveos.api.core;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Canonical date/time rules for API parsing, UTC instants, and user-local schedules. */
public final class DateUtils {

  private DateUtils() {}

  /** Parses a timezone-free ISO calendar date such as {@code 2026-09-18}. */
  public static LocalDate parseCalendarDate(String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeException | NullPointerException exception) {
      throw validation("Date must be a valid calendar day");
    }
  }

  /** Parses an absolute ISO-8601 instant. Offsets are normalized by {@link Instant}. */
  public static Instant parseInstant(String value) {
    if (value == null) return null;
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException exception) {
      throw validation("Time must be an ISO-8601 instant");
    }
  }

  /** Parses a nullable local wall-clock time used together with an IANA zone. */
  public static LocalTime parseLocalTime(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalTime.parse(value.trim());
    } catch (DateTimeParseException exception) {
      throw validation("quiet hours must use HH:mm or HH:mm:ss");
    }
  }

  /** Validates and returns an IANA timezone identifier, never a fixed-offset policy string. */
  public static ZoneId parseZoneId(String value) {
    String timezone = value == null ? "" : value.trim();
    try {
      ZoneId zone = ZoneId.of(timezone);
      if (zone instanceof ZoneOffset || "GMT".equals(timezone) || timezone.startsWith("Etc/GMT")) {
        throw new DateTimeException("Fixed offsets are not user timezones");
      }
      return zone;
    } catch (DateTimeException exception) {
      throw validation("timezone must be a valid IANA timezone");
    }
  }

  public static Instant now() {
    return Instant.now();
  }

  public static Instant now(Clock clock) {
    return Instant.now(clock);
  }

  public static LocalDate utcToday() {
    return LocalDate.now(ZoneOffset.UTC);
  }

  public static LocalDate utcToday(Clock clock) {
    return LocalDate.now(clock.withZone(ZoneOffset.UTC));
  }

  public static String format(LocalDate value) {
    return value == null ? null : value.toString();
  }

  public static String format(Instant value) {
    return value == null ? null : value.toString();
  }

  public static String format(LocalTime value) {
    return value == null ? null : value.toString();
  }

  public static long daysSince(LocalDate date, LocalDate today) {
    return ChronoUnit.DAYS.between(date, today);
  }

  /** Feb 29 recurrences use Feb 28 in non-leap years, matching existing Calendar behavior. */
  public static long daysUntilNextAnnualOccurrence(LocalDate original, LocalDate today) {
    MonthDay monthDay = MonthDay.from(original);
    LocalDate next = monthDay.atYear(today.getYear());
    if (next.isBefore(today)) next = monthDay.atYear(today.getYear() + 1);
    return ChronoUnit.DAYS.between(today, next);
  }

  /**
   * Checks a recipient-local quiet window at an absolute instant. Start is inclusive and end is
   * exclusive. Equal endpoints mean quiet all day; a start after end wraps across midnight.
   */
  public static boolean isInQuietWindow(
      Instant when, ZoneId zone, LocalTime quietStart, LocalTime quietEnd) {
    if (quietStart == null && quietEnd == null) return false;
    if (quietStart == null || quietEnd == null) {
      throw validation("quietStart and quietEnd must both be set or both be empty");
    }
    if (quietStart.equals(quietEnd)) return true;
    LocalTime local = when.atZone(zone).toLocalTime();
    if (quietStart.isBefore(quietEnd)) {
      return !local.isBefore(quietStart) && local.isBefore(quietEnd);
    }
    return !local.isBefore(quietStart) || local.isBefore(quietEnd);
  }

  private static AppException validation(String message) {
    return new AppException(ErrorCode.VALIDATION_ERROR, message);
  }
}
