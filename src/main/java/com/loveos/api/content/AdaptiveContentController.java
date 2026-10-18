package com.loveos.api.content;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.DateUtils;
import com.loveos.api.pairing.CoupleAccess;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/content")
public class AdaptiveContentController {
  private static final List<Suggestion> CATALOG = List.of(
      new Suggestion("remember", "Add one small moment from today to your story.", "memory"),
      new Suggestion("plan", "Choose one quiet hour this week that belongs only to you two.", "calendar"),
      new Suggestion("say", "Send one specific thing you appreciated about your partner today.", "chat"),
      new Suggestion("revisit", "Open an old memory together and add the detail you still remember.", "memory"),
      new Suggestion("future", "Put one tiny shared plan on the calendar before the week ends.", "calendar"));

  private final CoupleAccess couples;

  public AdaptiveContentController(CoupleAccess couples) {
    this.couples = couples;
  }

  @GetMapping("/suggestions")
  public ApiResponse<Suggestions> suggestions(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    UUID userId = principal.userId();
    UUID coupleId = couples.requireMembership(userId).coupleId();
    LocalDate today = DateUtils.utcToday();
    int start = Math.floorMod(coupleId.hashCode() * 31 + today.hashCode(), CATALOG.size());
    return ApiResponse.of(new Suggestions(today.toString(), List.of(
      CATALOG.get(start), CATALOG.get((start + 1) % CATALOG.size()))));
  }

  public record Suggestions(String generatedFor, List<Suggestion> items) {}
  public record Suggestion(String key, String text, String action) {}
}
