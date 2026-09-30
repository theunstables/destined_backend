package com.loveos.api.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CalendarServiceTest {

  @Test
  void annualOccurrenceWrapsIntoNextYear() {
    assertThat(CalendarService.daysUntilNextOccurrence(
        LocalDate.of(2020, 9, 17), LocalDate.of(2026, 9, 18))).isEqualTo(364);
  }

  @Test
  void occurrenceTodayHasZeroDaysRemaining() {
    assertThat(CalendarService.daysUntilNextOccurrence(
        LocalDate.of(2020, 9, 18), LocalDate.of(2026, 9, 18))).isZero();
  }

  @Test
  void leapDayUsesLastDayOfFebruaryInNonLeapYear() {
    assertThat(CalendarService.daysUntilNextOccurrence(
        LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28))).isZero();
  }
}