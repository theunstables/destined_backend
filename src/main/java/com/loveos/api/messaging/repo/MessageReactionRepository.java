package com.loveos.api.messaging.repo;

import com.loveos.api.messaging.domain.MessageReaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, UUID> {
  List<MessageReaction> findByMessageIdInOrderByCreatedAtAsc(List<UUID> messageIds);
  Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(
      UUID messageId, UUID userId, String emoji);
}
