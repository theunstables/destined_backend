package com.loveos.api.messaging.dto;

import java.util.List;

public final class MessagingResponses {
  private MessagingResponses() {}

  public record ReactionDto(String emoji, String authorId) {}

  public record MessageDto(
      String id,
      String authorId,
      String kind,
      String body,
      String mediaUri,
      Integer durationMs,
      String replyToId,
      List<ReactionDto> reactions,
      boolean pinned,
      String sentAt,
      String status,
      String clientId) {}

  public record ReadResult(List<String> messageIds) {}
}
