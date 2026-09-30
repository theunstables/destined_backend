package com.loveos.api.home.dto;

import com.loveos.api.calendar.dto.CalendarResponses;
import java.util.List;

public final class HomeResponses {
  private HomeResponses() {}

  public record HomeDto(
      String coupleName,
      String greetingName,
      Space space,
      Long daysTogether,
      List<Stat> stats,
      List<CalendarResponses.ComingUpDto> comingUp,
      List<RecentMemory> recentMemories,
      Pulse pulse) {}

  public record Space(String name, String shortName, String coverStyle) {}
  public record Stat(String key, String icon, String value, String label, String unit) {}
  public record RecentMemory(
      String id, String title, String date, String photoUri, String location) {}
  public record Pulse(String label, String value, String unit, String caption) {}
}