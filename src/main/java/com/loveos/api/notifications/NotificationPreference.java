package com.loveos.api.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
class NotificationPreference {

  @Id
  @Column(name = "user_id", columnDefinition = "uuid")
  private UUID userId;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(nullable = false)
  private boolean messages = true;

  @Column(nullable = false)
  private boolean occasions = true;

  @Column(nullable = false)
  private boolean memories = true;

  @Column(name = "quiet_start")
  private LocalTime quietStart;

  @Column(name = "quiet_end")
  private LocalTime quietEnd;

  @Column(nullable = false, length = 64)
  private String timezone = "UTC";

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
