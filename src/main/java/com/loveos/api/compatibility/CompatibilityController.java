package com.loveos.api.compatibility;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.DateUtils;
import com.loveos.api.pairing.CoupleAccess;
import com.loveos.api.story.StoryAccess;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/compatibility")
public class CompatibilityController {
  private final CoupleAccess couples;
  private final StoryAccess stories;

  public CompatibilityController(CoupleAccess couples, StoryAccess stories) {
    this.couples = couples;
    this.stories = stories;
  }

  @GetMapping
  public ApiResponse<Compatibility> get(@AuthenticationPrincipal AuthenticatedUser principal) {
    UUID viewer = principal.userId();
    List<Person> people = couples.members(viewer).stream()
        .sorted(Comparator.comparing(member -> member.userId().equals(viewer) ? 0 : 1))
        .map(member -> new Person(member.userId().toString(), name(member, viewer),
            member.birthday() == null ? null : member.birthday().toString()))
        .toList();
    LocalDate met = stories.metDate(viewer);
    Long daysTogether = met == null ? null : Math.max(0, DateUtils.daysSince(met, DateUtils.utcToday()));
    return ApiResponse.of(new Compatibility(people, daysTogether));
  }

  private static String name(CoupleAccess.Member member, UUID viewer) {
    if (member.nickname() != null && !member.nickname().isBlank()) return member.nickname().trim();
    if (member.displayName() != null && !member.displayName().isBlank()) return member.displayName().trim();
    return member.userId().equals(viewer) ? "You" : "Partner";
  }

  public record Compatibility(List<Person> people, Long daysTogether) {}
  public record Person(String id, String name, String birthday) {}
}
