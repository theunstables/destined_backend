package com.loveos.api.home;

import com.loveos.api.calendar.CalendarAccess;
import com.loveos.api.core.DateUtils;
import com.loveos.api.home.dto.HomeResponses;
import com.loveos.api.memories.MemoriesAccess;
import com.loveos.api.pairing.CoupleAccess;
import com.loveos.api.story.StoryAccess;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One read-only dashboard projection; source features remain the owners of all data. */
@Service
public class HomeService {

  private final CoupleAccess couples;
  private final StoryAccess story;
  private final MemoriesAccess memories;
  private final CalendarAccess calendar;

  public HomeService(
      CoupleAccess couples, StoryAccess story, MemoriesAccess memories, CalendarAccess calendar) {
    this.couples = couples;
    this.story = story;
    this.memories = memories;
    this.calendar = calendar;
  }

  @Transactional(readOnly = true)
  public HomeResponses.HomeDto getHome(UUID userId) {
    couples.requireMembership(userId);
    List<CoupleAccess.Member> members = couples.members(userId).stream()
        .sorted(Comparator.comparing(CoupleAccess.Member::role)).toList();
    CoupleAccess.Space space = couples.space(userId);
    LocalDate metDate = story.metDate(userId);
    MemoriesAccess.Summary memorySummary = memories.summary(userId, 6);
    var comingUp = calendar.upcoming(userId, 365, 5);

    Long together = metDate == null ? null
        : Math.max(0, DateUtils.daysSince(metDate, DateUtils.utcToday()));
    String greeting = members.stream().filter(member -> member.userId().equals(userId))
        .findFirst().map(HomeService::name).orElse("");
    String coupleName = members.stream().map(HomeService::name)
        .reduce((left, right) -> left + " & " + right).orElse("");

    List<HomeResponses.Stat> stats = List.of(
        stat("together", "heart", together == null ? "—" : number(together), "Together", "days"),
        stat("memories", "image", number(memorySummary.count()), "Memories", ""),
        stat("places", "map-pin", number(memorySummary.places()), "Places", ""),
        stat("trips", "map", number(memorySummary.trips()), "Trips", ""));

    List<HomeResponses.RecentMemory> recent = memorySummary.recent().stream()
        .map(memory -> new HomeResponses.RecentMemory(
            memory.id(), memory.title(), memory.date(), memory.photoUri(), memory.location()))
        .toList();
    HomeResponses.Pulse pulse = together == null ? null : new HomeResponses.Pulse(
        "Relationship pulse", number(together), "days", "since we first met.");

    return new HomeResponses.HomeDto(
        coupleName, greeting,
        new HomeResponses.Space(space.name(), space.shortName(), space.coverStyle()),
        together, stats, comingUp, recent, pulse);
  }

  private static HomeResponses.Stat stat(
      String key, String icon, String value, String label, String unit) {
    return new HomeResponses.Stat(key, icon, value, label, unit);
  }

  private static String name(CoupleAccess.Member member) {
    if (member.nickname() != null && !member.nickname().isBlank()) return member.nickname().trim();
    if (member.displayName() != null && !member.displayName().isBlank()) {
      return member.displayName().trim();
    }
    return member.role() == CoupleAccess.Role.FOUNDER ? "You" : "Partner";
  }

  private static String number(long value) {
    return NumberFormat.getIntegerInstance(Locale.US).format(value);
  }
}