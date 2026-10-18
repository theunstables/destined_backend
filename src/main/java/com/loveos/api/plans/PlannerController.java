package com.loveos.api.plans;

import tools.jackson.databind.JsonNode;
import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/planner")
public class PlannerController {
  private static final int MAX_NIGHTS = 14;
  private static final Set<String> STYLES = Set.of(
      "relaxing", "adventure", "romantic", "foodie", "culture", "nature", "surprise");
  private static final List<Destination> DESTINATIONS = List.of(
      new Destination("Paris, France", "Cafés, art, river walks, and quiet corners"),
      new Destination("Kyoto, Japan", "Temples, gardens, food, and mountain air"),
      new Destination("Amalfi, Italy", "Sea views, village paths, and long dinners"),
      new Destination("Lisbon, Portugal", "Hills, tiled streets, music, and pastries"),
      new Destination("Reykjavík, Iceland", "Hot springs, open roads, and northern skies"));

  private final CoupleAccess couples;

  public PlannerController(CoupleAccess couples) {
    this.couples = couples;
  }

  @GetMapping("/destinations")
  ApiResponse<List<Destination>> destinations(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    couples.requireMembership(userId(principal));
    return ApiResponse.of(DESTINATIONS);
  }

  @PostMapping("/generate")
  ApiResponse<TripDto> generate(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody GenerateTrip request) {
    couples.requireMembershipForUpdate(userId(principal));
    validate(request);
    return ApiResponse.of(plan(request));
  }

  private static TripDto plan(GenerateTrip request) {
    LocalDate start = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(30);
    LocalDate end = start.plusDays(request.nights());
    DateTimeFormatter date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.UK);
    List<DayDto> days = new ArrayList<>();
    int totalCost = 0;
    for (int index = 0; index <= request.nights(); index++) {
      LocalDate day = start.plusDays(index);
      List<MomentDto> moments = moments(request.destination(), request.styles(), index);
      totalCost += 72 + index * 8;
      days.add(new DayDto(
          UUID.randomUUID().toString(), index + 1,
          index == 0 ? "First steps together" : "A day to remember",
          day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.UK))
              + " — time left open for the two of you",
          moments));
    }
    return new TripDto(
        UUID.randomUUID().toString(), "Our Little Adventure", request.destination(),
        start.format(date) + " – " + end.format(date), null, request.nights(), 2,
        request.budgetBand(), totalCost, "£", request.styles(), days,
        days.size() * 3, days.size() * 3);
  }

  private static List<MomentDto> moments(String destination, List<String> styles, int day) {
    String focus = styles.isEmpty() ? "surprise" : styles.get(day % styles.size());
    return List.of(
        moment(day, 1, "09:30 AM", "MORNING", "Slow start in " + destination,
            "A relaxed beginning shaped around " + focus + ".", "£18 for two"),
        moment(day, 2, "02:00 PM", "AFTERNOON", "Explore somewhere new",
            "Walk, pause, and follow whatever catches your attention.", "£32 for two"),
        moment(day, 3, "07:00 PM", "EVENING", "Dinner and a shared favourite",
            "End the day with time to talk and no reason to hurry.", "£48 for two"));
  }

  private static MomentDto moment(
      int day, int number, String time, String slot, String title, String description, String cost) {
    return new MomentDto("day-" + (day + 1) + "-moment-" + number, time, slot, title,
        description, cost, null, null, false);
  }

  private static void validate(GenerateTrip request) {
    if (request.styles().stream().anyMatch(style -> !STYLES.contains(style))) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Unknown travel style");
    }
    JsonNode budget = request.budgetBand();
    boolean validBudget = budget != null && (
        (budget.isTextual() && budget.asText().equals("flexible"))
        || (budget.isInt() && Set.of(100, 250, 500).contains(budget.asInt())));
    if (!validBudget) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Unknown budget band");
    }
  }

  private static UUID userId(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal.userId();
  }

  public record GenerateTrip(
      @NotBlank @Size(max = 160) String destination,
      @Min(1) @Max(MAX_NIGHTS) int nights,
      @NotNull JsonNode budgetBand,
      @NotNull @Size(max = 7) List<@NotBlank String> styles) {}

  public record Destination(String name, String tagline) {}
  public record MomentDto(
      String id, String time, String slot, String title, String description, String cost,
      String photoUri, String photoCaption, boolean saved) {}
  public record DayDto(String id, int number, String title, String subtitle, List<MomentDto> moments) {}
  public record TripDto(
      String id, String title, String destination, String dateRange, String coverUri,
      int nights, int travellers, JsonNode budgetBand, int estimatedCost, String currency,
      List<String> styles, List<DayDto> days, int momentsPlanned, int momentsTotal) {}
}
