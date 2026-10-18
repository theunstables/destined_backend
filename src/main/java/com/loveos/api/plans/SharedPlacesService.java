package com.loveos.api.plans;

import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedPlacesService {
  private final CurrentLocationShareRepository repository;
  private final CoupleAccess couples;
  private final Clock clock;

  @Autowired
  public SharedPlacesService(CurrentLocationShareRepository repository, CoupleAccess couples) {
    this(repository, couples, Clock.systemUTC());
  }

  SharedPlacesService(CurrentLocationShareRepository repository, CoupleAccess couples, Clock clock) {
    this.repository = repository;
    this.couples = couples;
    this.clock = clock;
  }

  @Transactional
  public PlacesView get(UUID userId) {
    CoupleAccess.Membership membership = couples.requireMembership(userId);
    Instant now = clock.instant();
    List<CurrentLocationShare> rows = repository.findByCoupleId(membership.coupleId());
    List<CurrentLocationShare> expired = rows.stream().filter(row -> expired(row, now)).toList();
    if (!expired.isEmpty()) repository.deleteAllInBatch(expired);

    List<LocationView> active = rows.stream()
        .filter(row -> !expired(row, now))
        .map(row -> view(row, userId, now))
        .sorted(Comparator.comparing(LocationView::who))
        .toList();
    LocationView me = active.stream().filter(row -> row.who().equals("me")).findFirst().orElse(null);
    LocationView partner = active.stream().filter(row -> row.who().equals("partner")).findFirst().orElse(null);
    return new PlacesView(me, partner, distance(me, partner));
  }

  @Transactional
  public PlacesView share(UUID userId, ShareRequest request) {
    CoupleAccess.Membership membership = couples.requireMembershipForUpdate(userId);
    validate(request);
    Instant now = clock.instant();
    CurrentLocationShare row = repository.findById(userId).orElseGet(CurrentLocationShare::new);
    row.setUserId(userId);
    row.setCoupleId(membership.coupleId());
    row.setLatitude(request.latitude());
    row.setLongitude(request.longitude());
    row.setAccuracyMeters(request.accuracyMeters());
    row.setBattery(request.battery());
    row.setActivity(clean(request.activity()));
    row.setSharedUntil(expiry(request.window(), request.timezone(), now));
    row.setUpdatedAt(now);
    repository.saveAndFlush(row);
    return get(userId);
  }

  @Transactional
  public PlacesView pause(UUID userId) {
    couples.requireMembershipForUpdate(userId);
    repository.findById(userId).ifPresent(repository::delete);
    return get(userId);
  }

  private void validate(ShareRequest request) {
    if (request == null || request.window() == null
        || request.latitude() < -90 || request.latitude() > 90
        || request.longitude() < -180 || request.longitude() > 180
        || request.accuracyMeters() != null && request.accuracyMeters() < 0
        || request.battery() != null && (request.battery() < 0 || request.battery() > 100)) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Location share is invalid");
    }
    if (request.activity() != null && request.activity().length() > 80) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Activity must be at most 80 characters");
    }
  }

  private Instant expiry(ShareWindow window, String timezone, Instant now) {
    return switch (window) {
      case ONE_HOUR -> now.plus(1, ChronoUnit.HOURS);
      case TONIGHT -> {
        final ZoneId zone;
        try {
          zone = timezone == null || timezone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timezone);
        } catch (java.time.DateTimeException exception) {
          throw new AppException(ErrorCode.VALIDATION_ERROR, "Timezone is invalid");
        }
        ZonedDateTime local = now.atZone(zone);
        ZonedDateTime end = local.toLocalDate().plusDays(1).atStartOfDay(zone);
        yield end.toInstant();
      }
      case UNTIL_OFF -> null;
    };
  }

  private LocationView view(CurrentLocationShare row, UUID viewerId, Instant now) {
    String name = couples.members(viewerId).stream()
        .filter(member -> member.userId().equals(row.getUserId()))
        .map(member -> member.nickname() == null || member.nickname().isBlank()
            ? member.displayName() : member.nickname())
        .findFirst().orElse(row.getUserId().equals(viewerId) ? "You" : "Partner");
    long updatedSeconds = Math.max(0, Duration.between(row.getUpdatedAt(), now).toSeconds());
    Long remainingMinutes = row.getSharedUntil() == null ? null
        : Math.max(0, Duration.between(now, row.getSharedUntil()).toMinutes());
    return new LocationView(
        row.getUserId().equals(viewerId) ? "me" : "partner",
        name,
        row.getLatitude(),
        row.getLongitude(),
        row.getAccuracyMeters(),
        row.getBattery(),
        row.getActivity(),
        updatedSeconds,
        row.getSharedUntil(),
        remainingMinutes);
  }

  private static boolean expired(CurrentLocationShare row, Instant now) {
    return row.getSharedUntil() != null && !row.getSharedUntil().isAfter(now);
  }

  private static String clean(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private static Double distance(LocationView one, LocationView two) {
    if (one == null || two == null) return null;
    double earthRadiusKm = 6371.0088;
    double lat1 = Math.toRadians(one.latitude());
    double lat2 = Math.toRadians(two.latitude());
    double deltaLat = lat2 - lat1;
    double deltaLon = Math.toRadians(two.longitude() - one.longitude());
    double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
        + Math.cos(lat1) * Math.cos(lat2)
        * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
    return Math.round(earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 10) / 10.0;
  }

  public enum ShareWindow { ONE_HOUR, TONIGHT, UNTIL_OFF }

  public record ShareRequest(
      double latitude,
      double longitude,
      Double accuracyMeters,
      Integer battery,
      String activity,
      String timezone,
      ShareWindow window) {}

  public record LocationView(
      String who,
      String name,
      double latitude,
      double longitude,
      Double accuracyMeters,
      Integer battery,
      String activity,
      long updatedSecondsAgo,
      Instant sharedUntil,
      Long minutesRemaining) {}

  public record PlacesView(LocationView me, LocationView partner, Double distanceKilometers) {}
}
