package com.loveos.api.feedback;

import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {
  private static final Set<String> TOPICS = Set.of("PROBLEM", "IDEA", "OTHER");
  private final UserFeedbackRepository repository;
  private final CoupleAccess couples;

  public FeedbackService(UserFeedbackRepository repository, CoupleAccess couples) {
    this.repository = repository;
    this.couples = couples;
  }

  @Transactional
  public Receipt submit(UUID userId, Submission submission) {
    if (submission == null || submission.topic() == null || submission.message() == null) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Feedback is incomplete");
    }
    String topic = submission.topic().trim().toUpperCase(Locale.ROOT);
    String message = submission.message().trim();
    if (!TOPICS.contains(topic) || message.length() < 12 || message.length() > 4000) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Feedback is invalid");
    }
    UserFeedback row = new UserFeedback();
    row.setUserId(userId);
    try {
      row.setCoupleId(couples.requireMembership(userId).coupleId());
    } catch (AppException exception) {
      if (exception.code() != ErrorCode.NO_COUPLE) throw exception;
      row.setCoupleId(null);
    }
    row.setTopic(topic);
    row.setMessage(message);
    row.setStatus("NEW");
    row.setCreatedAt(Instant.now());
    repository.saveAndFlush(row);
    return new Receipt(row.getId(), row.getStatus(), row.getCreatedAt());
  }

  public record Submission(String topic, String message) {}
  public record Receipt(UUID id, String status, Instant createdAt) {}
}
