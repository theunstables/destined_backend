package com.loveos.api.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DateUtilsTest {

  @Test
  void calendarDatesAreStrictAndNeverTimezoneShifted() {
    assertThat(DateUtils.parseCalendarDate("2024-02-29")).isEqualTo(LocalDate.of(2024, 2, 29));
    assertThat(DateUtils.format(LocalDate.of(2026, 1, 2))).isEqualTo("2026-01-02");

    assertThatThrownBy(() -> DateUtils.parseCalendarDate("2026-02-29"))
        .isInstanceOf(AppException.class)
        .extracting(error -> ((AppException) error).code())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThatThrownBy(() -> DateUtils.parseCalendarDate("02/28/2026"))
        .isInstanceOf(AppException.class);
  }

  @Test
  void instantsNormalizeOffsetsAndUtcDayUsesTheFixedClock() {
    Instant instant = DateUtils.parseInstant("2026-01-01T14:00:00+14:00");
    assertThat(instant).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(DateUtils.format(instant)).isEqualTo("2026-01-01T00:00:00Z");

    Clock dateLineClock = Clock.fixed(
        Instant.parse("2025-12-31T23:30:00Z"), ZoneId.of("Pacific/Kiritimati"));
    assertThat(DateUtils.utcToday(dateLineClock)).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  @Test
  void timezonePolicyAcceptsIanaRegionsAndRejectsFixedOffsets() {
    assertThat(DateUtils.parseZoneId("America/New_York"))
        .isEqualTo(ZoneId.of("America/New_York"));
    assertThat(DateUtils.parseZoneId("UTC").getId()).isEqualTo("UTC");

    assertThatThrownBy(() -> DateUtils.parseZoneId("+05:30"))
        .isInstanceOf(AppException.class);
    assertThatThrownBy(() -> DateUtils.parseZoneId("Etc/GMT+5"))
        .isInstanceOf(AppException.class);
  }

  @Test
  void annualRecurrenceUsesFebruary28InNonLeapYears() {
    LocalDate leapDay = LocalDate.of(2024, 2, 29);

    assertThat(DateUtils.daysUntilNextAnnualOccurrence(
        leapDay, LocalDate.of(2026, 2, 27))).isEqualTo(1);
    assertThat(DateUtils.daysUntilNextAnnualOccurrence(
        leapDay, LocalDate.of(2026, 2, 28))).isZero();
    assertThat(DateUtils.daysUntilNextAnnualOccurrence(
        leapDay, LocalDate.of(2026, 3, 1))).isEqualTo(364);
  }

  @Test
  void quietWindowsUseRecipientLocalTimeAcrossMidnight() {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    LocalTime start = LocalTime.of(22, 0);
    LocalTime end = LocalTime.of(7, 0);

    assertThat(DateUtils.isInQuietWindow(
        Instant.parse("2026-06-01T14:00:00Z"), tokyo, start, end)).isTrue();
    assertThat(DateUtils.isInQuietWindow(
        Instant.parse("2026-06-01T22:00:00Z"), tokyo, start, end)).isFalse();
    assertThat(DateUtils.isInQuietWindow(
        Instant.parse("2026-06-01T12:00:00Z"), tokyo, start, start)).isTrue();
    assertThat(DateUtils.isInQuietWindow(
        Instant.parse("2026-06-01T12:00:00Z"), tokyo, null, null)).isFalse();
  }

  @Test
  void quietWindowsFollowDstGapAndOverlapZoneRules() {
    ZoneId newYork = ZoneId.of("America/New_York");
    LocalTime start = LocalTime.of(1, 0);
    LocalTime end = LocalTime.of(3, 0);

    assertThat(DateUtils.isInQuietWindow(
        Instant.parse("2026-03-08T06:30:00Z"), newYork, start, end)).isTrue();
    assertThat(DateUtils.isInQuietWindow(
        Instant.parse("2026-03-08T07:30:00Z"), newYork, start, end)).isFalse();
    assertThat(DateUtils.isInQuietWindow(
        Instant.parse("2026-11-01T05:30:00Z"), newYork, start, end)).isTrue();
    assertThat(DateUtils.isInQuietWindow(
        Instant.parse("2026-11-01T06:30:00Z"), newYork, start, end)).isTrue();
  }

  @Test
  void partialQuietWindowIsRejected() {
    assertThatThrownBy(() -> DateUtils.isInQuietWindow(
        Instant.EPOCH, ZoneOffset.UTC, LocalTime.MIDNIGHT, null))
        .isInstanceOf(AppException.class);
  }
}
