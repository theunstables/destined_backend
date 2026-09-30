package com.loveos.api.messaging.repo;

import com.loveos.api.messaging.domain.MessageReceipt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, UUID> {
  List<MessageReceipt> findByMessageIdIn(List<UUID> messageIds);
  Optional<MessageReceipt> findByMessageIdAndUserId(UUID messageId, UUID userId);

  @Query("""
      select r from MessageReceipt r, Message m
      where r.messageId = m.id and r.userId = :userId and r.readAt is null
        and m.coupleId = :coupleId and m.senderId <> :userId
        and m.deletedAt is null and m.sentAt <= :upTo
      """)
  List<MessageReceipt> pendingRead(
      UUID userId, UUID coupleId, Instant upTo);

  @Query("""
      select r from MessageReceipt r, Message m
      where r.messageId = m.id and r.userId = :userId and r.deliveredAt is null
        and m.coupleId = :coupleId and m.senderId <> :userId and m.deletedAt is null
      """)
  List<MessageReceipt> pendingDelivery(UUID userId, UUID coupleId);
}
