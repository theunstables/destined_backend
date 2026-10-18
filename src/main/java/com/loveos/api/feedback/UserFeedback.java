package com.loveos.api.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_feedback")
public class UserFeedback {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "couple_id") private UUID coupleId;
  @Column(nullable = false, length = 20) private String topic;
  @Column(nullable = false, length = 4000) private String message;
  @Column(nullable = false, length = 20) private String status;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public UUID getCoupleId() { return coupleId; }
  public void setCoupleId(UUID coupleId) { this.coupleId = coupleId; }
  public String getTopic() { return topic; }
  public void setTopic(String topic) { this.topic = topic; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
