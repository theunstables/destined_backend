package com.loveos.api.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loveos.api.pairing.CoupleAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SharedPlacesServiceTest {
  @Mock CurrentLocationShareRepository repository;
  @Mock CoupleAccess couples;
  @Captor ArgumentCaptor<CurrentLocationShare> saved;

  private final UUID me = UUID.randomUUID();
  private final UUID partner = UUID.randomUUID();
  private final UUID coupleId = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-10-13T12:00:00Z");
  private SharedPlacesService service;

  @BeforeEach
  void setUp() {
    service = new SharedPlacesService(
        repository, couples, Clock.fixed(now, ZoneOffset.UTC));
    when(couples.requireMembership(me)).thenReturn(
        new CoupleAccess.Membership(coupleId, CoupleAccess.Role.FOUNDER, true));
  }

  @Test
  void replacesCurrentFixWithServerControlledExpiry() {
    when(couples.requireMembershipForUpdate(me)).thenReturn(
        new CoupleAccess.Membership(coupleId, CoupleAccess.Role.FOUNDER, true));
    when(repository.findById(me)).thenReturn(Optional.empty());
    when(repository.findByCoupleId(coupleId)).thenReturn(List.of());

    service.share(me, new SharedPlacesService.ShareRequest(
      51.5074, -0.1278, 8.0, 72, "Walking", "Europe/London",
      SharedPlacesService.ShareWindow.ONE_HOUR));

    verify(repository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getSharedUntil()).isEqualTo(now.plusSeconds(3600));
    assertThat(saved.getValue().getCoupleId()).isEqualTo(coupleId);
  }

  @Test
  void removesExpiredRowsAndNeverReturnsLocationHistory() {
    CurrentLocationShare expired = location(partner, now.minusSeconds(1), 51.5, -0.1);
    when(repository.findByCoupleId(coupleId)).thenReturn(List.of(expired));

    SharedPlacesService.PlacesView view = service.get(me);

    assertThat(view.partner()).isNull();
    verify(repository).deleteAllInBatch(List.of(expired));
  }

  @Test
  void returnsViewerRelativeLocationsAndDistance() {
    when(couples.members(me)).thenReturn(List.of(
      new CoupleAccess.Member(me, CoupleAccess.Role.FOUNDER, "Alex", null),
      new CoupleAccess.Member(partner, CoupleAccess.Role.MEMBER, "Sam", "Sunny")));
    CurrentLocationShare mine = location(me, null, 51.5007, -0.1246);
    CurrentLocationShare theirs = location(partner, now.plusSeconds(3600), 51.5155, -0.0922);
    when(repository.findByCoupleId(coupleId)).thenReturn(List.of(mine, theirs));

    SharedPlacesService.PlacesView view = service.get(me);

    assertThat(view.me().who()).isEqualTo("me");
    assertThat(view.partner().who()).isEqualTo("partner");
    assertThat(view.partner().name()).isEqualTo("Sunny");
    assertThat(view.distanceKilometers()).isPositive();
  }

  private CurrentLocationShare location(
      UUID userId, Instant sharedUntil, double latitude, double longitude) {
    CurrentLocationShare row = new CurrentLocationShare();
    row.setUserId(userId);
    row.setCoupleId(coupleId);
    row.setLatitude(latitude);
    row.setLongitude(longitude);
    row.setUpdatedAt(now.minusSeconds(30));
    row.setSharedUntil(sharedUntil);
    return row;
  }
}
