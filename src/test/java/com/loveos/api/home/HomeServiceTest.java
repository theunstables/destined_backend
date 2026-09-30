package com.loveos.api.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.loveos.api.calendar.CalendarAccess;
import com.loveos.api.calendar.dto.CalendarResponses;
import com.loveos.api.memories.MemoriesAccess;
import com.loveos.api.pairing.CoupleAccess;
import com.loveos.api.story.StoryAccess;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeServiceTest {
  private CoupleAccess couples;
  private StoryAccess story;
  private MemoriesAccess memories;
  private CalendarAccess calendar;
  private HomeService service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    couples = org.mockito.Mockito.mock(CoupleAccess.class);
    story = org.mockito.Mockito.mock(StoryAccess.class);
    memories = org.mockito.Mockito.mock(MemoriesAccess.class);
    calendar = org.mockito.Mockito.mock(CalendarAccess.class);
    service = new HomeService(couples, story, memories, calendar);
    userId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();
    when(couples.requireMembership(userId)).thenReturn(
        new CoupleAccess.Membership(UUID.randomUUID(), CoupleAccess.Role.FOUNDER, true));
    when(couples.members(userId)).thenReturn(List.of(
        new CoupleAccess.Member(partnerId, CoupleAccess.Role.MEMBER, "Sam", null),
        new CoupleAccess.Member(userId, CoupleAccess.Role.FOUNDER, "Alex", "Al")));
    when(couples.space(userId)).thenReturn(new CoupleAccess.Space("Our Place", "Us", "night"));
    when(memories.summary(userId, 6)).thenReturn(new MemoriesAccess.Summary(42, 12, 8,
        List.of(new MemoriesAccess.RecentMemory("id", "Rome", "2024-06-12",
            "https://example.test/rome.jpg", "Rome"))));
    when(calendar.upcoming(userId, 365, 5)).thenReturn(List.of(
        new CalendarResponses.ComingUpDto("anniversary", "Our Anniversary", "Oct 14", 26,
            "2020-10-14", "anniversary", "keyDate")));
  }

  @Test
  void composesStableDashboardFromFeatureBoundaries() {
    LocalDate met = LocalDate.now(ZoneOffset.UTC).minusDays(1395);
    when(story.metDate(userId)).thenReturn(met);

    var result = service.getHome(userId);

    assertThat(result.coupleName()).isEqualTo("Al & Sam");
    assertThat(result.greetingName()).isEqualTo("Al");
    assertThat(result.daysTogether()).isEqualTo(1395);
    assertThat(result.stats()).extracting(stat -> stat.key())
        .containsExactly("together", "memories", "places", "trips");
    assertThat(result.stats().get(1).value()).isEqualTo("42");
    assertThat(result.recentMemories()).singleElement()
        .extracting(memory -> memory.photoUri()).isEqualTo("https://example.test/rome.jpg");
    assertThat(result.comingUp()).hasSize(1);
    assertThat(result.pulse()).isNotNull();
  }

  @Test
  void missingMetDateProducesUnknownTogetherAndNoPulse() {
    when(story.metDate(userId)).thenReturn(null);

    var result = service.getHome(userId);

    assertThat(result.daysTogether()).isNull();
    assertThat(result.stats().getFirst().value()).isEqualTo("—");
    assertThat(result.pulse()).isNull();
  }
}