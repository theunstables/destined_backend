package com.loveos.api.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {
  @Mock UserFeedbackRepository repository;
  @Mock CoupleAccess couples;

  private final UUID userId = UUID.randomUUID();
  private final UUID coupleId = UUID.randomUUID();

  @Test
  void normalizesAndStoresCoupleScopedFeedback() {
    when(couples.requireMembership(userId)).thenReturn(new CoupleAccess.Membership(
        coupleId, CoupleAccess.Role.FOUNDER, true));
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var service = new FeedbackService(repository, couples);

    var receipt = service.submit(userId,
        new FeedbackService.Submission(" idea ", "  A useful improvement for the app.  "));

    var captor = ArgumentCaptor.forClass(UserFeedback.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    assertThat(captor.getValue().getCoupleId()).isEqualTo(coupleId);
    assertThat(captor.getValue().getTopic()).isEqualTo("IDEA");
    assertThat(captor.getValue().getMessage()).isEqualTo("A useful improvement for the app.");
    assertThat(receipt.status()).isEqualTo("NEW");
    assertThat(receipt.createdAt()).isNotNull();
  }

  @Test
  void allowsFeedbackBeforePairingButDoesNotSuppressOtherAccessFailures() {
    when(couples.requireMembership(userId)).thenThrow(
        new AppException(ErrorCode.NO_COUPLE, "Not paired"));
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var service = new FeedbackService(repository, couples);

    service.submit(userId,
        new FeedbackService.Submission("problem", "A complete problem description."));

    var captor = ArgumentCaptor.forClass(UserFeedback.class);
    verify(repository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getCoupleId()).isNull();
  }

  @Test
  void propagatesNonPairingAccessFailures() {
    when(couples.requireMembership(userId)).thenThrow(
        new AppException(ErrorCode.FORBIDDEN, "Access denied"));
    var service = new FeedbackService(repository, couples);

    assertThatThrownBy(() -> service.submit(userId,
        new FeedbackService.Submission("other", "A complete feedback message.")))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Access denied");
  }

  @Test
  void rejectsUnsupportedTopicsAndShortMessages() {
    var service = new FeedbackService(repository, couples);

    assertThatThrownBy(() -> service.submit(userId,
        new FeedbackService.Submission("billing", "A complete feedback message.")))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("invalid");
    assertThatThrownBy(() -> service.submit(userId,
        new FeedbackService.Submission("idea", "Too short")))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("invalid");
  }
}
