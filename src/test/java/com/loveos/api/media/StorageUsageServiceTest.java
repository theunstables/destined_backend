package com.loveos.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loveos.api.pairing.CoupleAccess;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageUsageServiceTest {
  @Mock CoupleAccess couples;
  @Mock MediaObjectRepository objects;

  private final UUID userId = UUID.randomUUID();
  private final UUID coupleId = UUID.randomUUID();

  @Test
  void totalsOnlyTheAuthenticatedCouplesUploadedMediaByCategory() {
    when(couples.requireMembership(userId)).thenReturn(new CoupleAccess.Membership(
        coupleId, CoupleAccess.Role.MEMBER, true));
    when(objects.findByCoupleId(coupleId)).thenReturn(List.of(
        media("image/jpeg", 100),
        media("image/png", 25),
        media("video/mp4", 500),
        media("audio/mpeg", 75),
        media("application/pdf", 900)));

    var usage = new StorageUsageService(couples, objects).get(userId);

    assertThat(usage.photoBytes()).isEqualTo(125);
    assertThat(usage.videoBytes()).isEqualTo(500);
    assertThat(usage.audioBytes()).isEqualTo(75);
    assertThat(usage.totalBytes()).isEqualTo(700);
    verify(objects).findByCoupleId(coupleId);
  }

  private static MediaObject media(String contentType, long bytes) {
    var row = new MediaObject();
    row.setContentType(contentType);
    row.setBytes(bytes);
    return row;
  }
}
