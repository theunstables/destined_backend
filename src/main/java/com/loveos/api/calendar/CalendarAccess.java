package com.loveos.api.calendar;

import com.loveos.api.calendar.dto.CalendarResponses;
import java.util.List;
import java.util.UUID;

/** Read-only Calendar projection used by Home. */
public interface CalendarAccess {
  List<CalendarResponses.ComingUpDto> upcoming(UUID userId, int withinDays, int limit);
}