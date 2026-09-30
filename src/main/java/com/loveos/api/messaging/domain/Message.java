package com.loveos.api.messaging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "couple_id", nullable = false)
  private UUID coupleId;

  @Column(name = "sender_id", nullable = false)
  private UUID senderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MessageKind kind;

  @Column(length = 4000)
  private String body;

  @Column(name = "media_url", length = 2048)
  private String mediaUrl;

  @Column(name = "duration_ms")
  private Integer durationMs;

  @Column(name = "reply_to_id")
  private UUID replyToId;

  @Column(name = "client_id", length = 64)
  private String clientId;

  @Column(nullable = false)
  private boolean pinned;

  @CreationTimestamp
  @Column(name = "sent_at", nullable = false, updatable = false)
  private Instant sentAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
