package com.loveos.api.messaging;

import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.messaging.domain.Message;
import com.loveos.api.messaging.domain.MessageKind;
import com.loveos.api.messaging.domain.MessageReaction;
import com.loveos.api.messaging.domain.MessageReceipt;
import com.loveos.api.messaging.dto.MessagingRequests;
import com.loveos.api.messaging.dto.MessagingResponses;
import com.loveos.api.messaging.repo.MessageReactionRepository;
import com.loveos.api.messaging.repo.MessageReceiptRepository;
import com.loveos.api.messaging.repo.MessageRepository;
import com.loveos.api.pairing.CoupleAccess;
import com.loveos.api.realtime.RealtimeHub;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MessagingService {
  private final CoupleAccess couples;
  private final MessageRepository messages;
  private final MessageReactionRepository reactions;
  private final MessageReceiptRepository receipts;
  private final RealtimeHub realtime;

  public MessagingService(
      CoupleAccess couples,
      MessageRepository messages,
      MessageReactionRepository reactions,
      MessageReceiptRepository receipts,
      RealtimeHub realtime) {
    this.couples = couples;
    this.messages = messages;
    this.reactions = reactions;
    this.receipts = receipts;
    this.realtime = realtime;
  }

  @Transactional(readOnly = true)
  public MessagePage list(UUID userId, String cursorText, int limit) {
    Context context = context(userId, false);
    var page = PageRequest.of(0, limit + 1);
    List<Message> rows;
    if (cursorText == null) {
      rows = messages.findByCoupleIdAndDeletedAtIsNullOrderBySentAtDescIdDesc(
          context.coupleId(), page);
    } else {
      UUID cursorId = id(cursorText);
      Message cursor = active(context.coupleId(), cursorId);
      rows = messages.pageBefore(context.coupleId(), cursor.getSentAt(), cursorId, page);
    }
    boolean more = rows.size() > limit;
    List<Message> selected = new ArrayList<>(rows.subList(0, Math.min(limit, rows.size())));
    String nextCursor = more ? selected.get(selected.size() - 1).getId().toString() : null;
    Collections.reverse(selected);
    return new MessagePage(dto(selected, userId), nextCursor);
  }

  @Transactional(readOnly = true)
  public List<MessagingResponses.MessageDto> pinned(UUID userId) {
    Context context = context(userId, false);
    return dto(messages.findByCoupleIdAndPinnedTrueAndDeletedAtIsNullOrderBySentAtDesc(
        context.coupleId(), PageRequest.of(0, 50)), userId);
  }

  @Transactional(readOnly = true)
  public List<MessagingResponses.MessageDto> search(UUID userId, String query, int limit) {
    Context context = context(userId, false);
    String value = query == null ? "" : query.trim();
    if (value.isEmpty()) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Search query is required");
    }
    return dto(messages.search(context.coupleId(), value, PageRequest.of(0, limit)), userId);
  }

  @Transactional
  public SendResult send(UUID userId, MessagingRequests.SendMessage input) {
    Context context = context(userId, true);
    String clientId = optional(input.clientId());
    if (clientId != null) {
      var existing = messages.findByCoupleIdAndClientId(context.coupleId(), clientId);
      if (existing.isPresent()) {
        return new SendResult(dto(existing.get(), userId), true, existing.get().getId());
      }
    }

    UUID replyToId = input.replyToId() == null ? null : id(input.replyToId());
    if (replyToId != null) active(context.coupleId(), replyToId);

    Message message = new Message();
    message.setCoupleId(context.coupleId());
    message.setSenderId(userId);
    message.setKind(kind(input.kind()));
    message.setBody(optional(input.body()));
    message.setMediaUrl(optional(input.mediaUri()));
    message.setDurationMs(input.durationMs());
    message.setReplyToId(replyToId);
    message.setClientId(clientId);
    message = messages.saveAndFlush(message);

    MessageReceipt receipt = new MessageReceipt();
    receipt.setMessageId(message.getId());
    receipt.setUserId(context.partnerId());
    receipts.saveAndFlush(receipt);
    var senderMessage = dto(message, userId);
    var partnerMessage = dto(message, context.partnerId());
    afterCommit(() -> realtime.publishToUser(context.coupleId(), context.partnerId(),
      Map.of("type", "message", "message", partnerMessage)));
    return new SendResult(senderMessage, false, message.getId());
  }

  @Transactional
  public MessagingResponses.MessageDto toggleReaction(UUID userId, UUID messageId, String emoji) {
    Context context = context(userId, true);
    Message message = active(context.coupleId(), messageId);
    String value = emoji.trim();
    reactions.findByMessageIdAndUserIdAndEmoji(messageId, userId, value)
        .ifPresentOrElse(reactions::delete, () -> {
          MessageReaction reaction = new MessageReaction();
          reaction.setMessageId(messageId);
          reaction.setUserId(userId);
          reaction.setEmoji(value);
          reactions.save(reaction);
        });
    reactions.flush();
        var viewerMessage = dto(message, userId);
        var partnerMessage = dto(message, context.partnerId());
        afterCommit(() -> {
          realtime.publishToUser(context.coupleId(), userId,
            Map.of("type", "reaction", "message", viewerMessage));
          realtime.publishToUser(context.coupleId(), context.partnerId(),
            Map.of("type", "reaction", "message", partnerMessage));
        });
        return viewerMessage;
  }

  @Transactional
  public MessagingResponses.MessageDto togglePin(UUID userId, UUID messageId) {
    Context context = context(userId, true);
    Message message = active(context.coupleId(), messageId);
    message.setPinned(!message.isPinned());
    message = messages.saveAndFlush(message);
    var viewerMessage = dto(message, userId);
    var partnerMessage = dto(message, context.partnerId());
    afterCommit(() -> {
      realtime.publishToUser(context.coupleId(), userId,
        Map.of("type", "pin", "message", viewerMessage));
      realtime.publishToUser(context.coupleId(), context.partnerId(),
        Map.of("type", "pin", "message", partnerMessage));
    });
    return viewerMessage;
  }

  @Transactional
  public MessagingResponses.ReadResult markRead(UUID userId, UUID upToMessageId) {
    Context context = context(userId, true);
    Message marker = active(context.coupleId(), upToMessageId);
    Instant now = Instant.now();
    List<String> changed = new ArrayList<>();
    for (MessageReceipt receipt : receipts.pendingRead(
        userId, context.coupleId(), marker.getSentAt())) {
      if (receipt.getDeliveredAt() == null) receipt.setDeliveredAt(now);
      receipt.setReadAt(now);
      changed.add(receipt.getMessageId().toString());
    }
    afterCommit(() -> changed.forEach(messageId ->
        realtime.publishToUser(context.coupleId(), context.partnerId(),
          Map.of("type", "status", "messageId", messageId, "status", "read"))));
    return new MessagingResponses.ReadResult(changed);
  }

  @Transactional
  public List<String> markDelivered(UUID userId) {
    Context context = context(userId, true);
    Instant now = Instant.now();
    List<String> changed = new ArrayList<>();
    for (MessageReceipt receipt : receipts.pendingDelivery(userId, context.coupleId())) {
      receipt.setDeliveredAt(now);
      changed.add(receipt.getMessageId().toString());
    }
    afterCommit(() -> changed.forEach(messageId ->
        realtime.publishToUser(context.coupleId(), context.partnerId(),
          Map.of("type", "status", "messageId", messageId, "status", "delivered"))));
    return changed;
  }

  @Transactional
  public void delete(UUID userId, UUID messageId) {
    Context context = context(userId, true);
    Message message = active(context.coupleId(), messageId);
    if (!message.getSenderId().equals(userId)) throw AppException.notFound("Message not found");
    message.setDeletedAt(Instant.now());
    messages.save(message);
  }

  @Transactional(readOnly = true)
  public MessagingResponses.MessageDto get(UUID userId, UUID messageId) {
    Context context = context(userId, false);
    return dto(active(context.coupleId(), messageId), userId);
  }

  private Context context(UUID userId, boolean lock) {
    CoupleAccess.Membership membership = lock
        ? couples.requireMembershipForUpdate(userId) : couples.requireMembership(userId);
    if (membership.state() == CoupleAccess.State.PENDING) {
      throw new AppException(ErrorCode.FORBIDDEN, "Connect with your partner to use chat");
    }
    UUID partnerId = couples.members(userId).stream()
        .map(CoupleAccess.Member::userId)
        .filter(id -> !id.equals(userId))
        .findFirst()
        .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "Partner is not connected"));
    return new Context(membership.coupleId(), partnerId);
  }

  private Message active(UUID coupleId, UUID id) {
    return messages.findByIdAndCoupleIdAndDeletedAtIsNull(id, coupleId)
        .orElseThrow(() -> AppException.notFound("Message not found"));
  }

  private List<MessagingResponses.MessageDto> dto(List<Message> rows, UUID viewerId) {
    if (rows.isEmpty()) return List.of();
    List<UUID> ids = rows.stream().map(Message::getId).toList();
    Map<UUID, List<MessageReaction>> reactionMap = new HashMap<>();
    reactions.findByMessageIdInOrderByCreatedAtAsc(ids)
        .forEach(reaction -> reactionMap.computeIfAbsent(reaction.getMessageId(), ignored -> new ArrayList<>())
            .add(reaction));
    Map<UUID, List<MessageReceipt>> receiptMap = new HashMap<>();
    receipts.findByMessageIdIn(ids)
        .forEach(receipt -> receiptMap.computeIfAbsent(receipt.getMessageId(), ignored -> new ArrayList<>())
            .add(receipt));
    return rows.stream().map(row -> dto(row, viewerId,
        reactionMap.getOrDefault(row.getId(), List.of()),
        receiptMap.getOrDefault(row.getId(), List.of()))).toList();
  }

  private MessagingResponses.MessageDto dto(Message row, UUID viewerId) {
    return dto(List.of(row), viewerId).getFirst();
  }

  private static MessagingResponses.MessageDto dto(
      Message row, UUID viewerId, List<MessageReaction> reactions, List<MessageReceipt> receipts) {
    boolean mine = row.getSenderId().equals(viewerId);
    MessageReceipt recipient = receipts.stream()
        .filter(receipt -> !receipt.getUserId().equals(row.getSenderId()))
        .findFirst().orElse(null);
    String status = !mine ? "read"
        : recipient != null && recipient.getReadAt() != null ? "read"
        : recipient != null && recipient.getDeliveredAt() != null ? "delivered" : "sent";
    return new MessagingResponses.MessageDto(
        row.getId().toString(), mine ? "me" : "partner",
        row.getKind().name().toLowerCase(Locale.ROOT), row.getBody(), row.getMediaUrl(),
        row.getDurationMs(), text(row.getReplyToId()),
        reactions.stream().map(reaction -> new MessagingResponses.ReactionDto(
            reaction.getEmoji(), reaction.getUserId().equals(viewerId) ? "me" : "partner")).toList(),
        row.isPinned(), row.getSentAt().toString(), status, row.getClientId());
  }

  public static UUID id(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Unknown message");
    }
  }

  private static MessageKind kind(String value) {
    return MessageKind.valueOf(value.toUpperCase(Locale.ROOT));
  }

  private static String optional(String value) {
    if (value == null) return null;
    String result = value.trim();
    return result.isEmpty() ? null : result;
  }

  private static String text(UUID value) {
    return value == null ? null : value.toString();
  }

  private static void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() {
        action.run();
      }
    });
  }

  private record Context(UUID coupleId, UUID partnerId) {}
  public record MessagePage(List<MessagingResponses.MessageDto> items, String nextCursor) {}
  public record SendResult(
      MessagingResponses.MessageDto message, boolean duplicate, UUID messageId) {}
}
