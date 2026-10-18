package com.loveos.api.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.loveos.api.messaging.MessagingService;
import com.loveos.api.messaging.dto.MessagingResponses;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class ChatWebSocketHandlerTest {
  private RealtimeHub hub;
  private MessagingService messaging;
  private ChatWebSocketHandler handler;
  private WebSocketSession session;
  private UUID userId;
  private UUID coupleId;
  private UUID partnerId;

  @BeforeEach
  void setUp() {
    hub = mock(RealtimeHub.class);
    messaging = mock(MessagingService.class);
    handler = new ChatWebSocketHandler(hub, messaging, JsonMapper.builder().build());
    session = mock(WebSocketSession.class);
    userId = UUID.randomUUID();
    coupleId = UUID.randomUUID();
    partnerId = UUID.randomUUID();
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(RealtimeHandshakeInterceptor.USER_ID, userId);
    attributes.put(RealtimeHandshakeInterceptor.COUPLE_ID, coupleId);
    attributes.put(RealtimeHandshakeInterceptor.PARTNER_ID, partnerId);
    when(session.getAttributes()).thenReturn(attributes);
  }

  @Test
  void connectionRegistersAndMarksPendingMessagesDelivered() throws Exception {
    when(messaging.markDelivered(userId)).thenReturn(List.of("message-1"));

    handler.afterConnectionEstablished(session);

    verify(hub).register(session, userId, coupleId);
    verify(messaging).markDelivered(userId);
  }

  @Test
  void typingIsEphemeralAndPublishedOnlyToPartner() throws Exception {
    handler.handleTextMessage(session, new TextMessage("{\"type\":\"typing\",\"isTyping\":true}"));

    verify(hub).publishExceptUser(coupleId, userId,
        Map.of("type", "typing", "isTyping", true, "from", userId.toString()));
  }

  @Test
  void readFrameUsesDurableReceiptService() throws Exception {
    UUID messageId = UUID.randomUUID();
    when(messaging.markRead(userId, messageId))
        .thenReturn(new MessagingResponses.ReadResult(List.of(messageId.toString())));

    handler.handleTextMessage(session,
        new TextMessage("{\"type\":\"read\",\"upToMessageId\":\"" + messageId + "\"}"));

    verify(messaging).markRead(userId, messageId);
  }

  @Test
  void watchJoinReportsWhetherPartnerIsOnlineAndNotifiesPartner() throws Exception {
    when(hub.online(coupleId, partnerId)).thenReturn(true);
    when(session.isOpen()).thenReturn(true);

    handler.handleTextMessage(session, new TextMessage("{\"type\":\"watch:join\"}"));

    verify(hub).publishExceptUser(coupleId, userId,
        Map.of("type", "watch:presence", "connected", true));
  }

  @Test
  void watchWhisperIsTrimmedAndRelayedOnlyToPartner() throws Exception {
    handler.handleTextMessage(session, new TextMessage(
        "{\"type\":\"watch:whisper\",\"text\":\"  hello  \",\"positionSeconds\":12}"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> event = ArgumentCaptor.forClass(Map.class);
    verify(hub).publishExceptUser(org.mockito.ArgumentMatchers.eq(coupleId),
        org.mockito.ArgumentMatchers.eq(userId), event.capture());
    assertThat(event.getValue()).containsEntry("type", "watch:whisper")
        .containsEntry("from", "partner").containsEntry("text", "hello")
        .containsEntry("positionSeconds", 12.0);
  }

  @Test
  void watchReactionRejectsEmojiOutsideTheProtocolAllowList() throws Exception {
    handler.handleTextMessage(session,
        new TextMessage("{\"type\":\"watch:reaction\",\"emoji\":\"🔥\"}"));

    verify(hub, never()).publishExceptUser(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void callLifecycleSignalIsRelayedOnlyToPartner() throws Exception {
    handler.handleTextMessage(session, new TextMessage(
        "{\"type\":\"call:signal\",\"callId\":\"call-12345678\",\"mode\":\"video\",\"action\":\"invite\"}"));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> event = ArgumentCaptor.forClass(Map.class);
    verify(hub).publishExceptUser(org.mockito.ArgumentMatchers.eq(coupleId),
        org.mockito.ArgumentMatchers.eq(userId), event.capture());
    assertThat(event.getValue()).containsEntry("type", "call:signal")
        .containsEntry("callId", "call-12345678")
        .containsEntry("mode", "video")
        .containsEntry("action", "invite")
        .containsEntry("from", userId.toString());
    assertThat(event.getValue().get("sentAt")).isInstanceOf(Long.class);
    }

    @Test
    void callLifecycleRejectsInvalidIdentifiersAndActions() throws Exception {
    handler.handleTextMessage(session, new TextMessage(
      "{\"type\":\"call:signal\",\"callId\":\"short\",\"mode\":\"video\",\"action\":\"invite\"}"));
    handler.handleTextMessage(session, new TextMessage(
      "{\"type\":\"call:signal\",\"callId\":\"call-12345678\",\"mode\":\"video\",\"action\":\"connect-media\"}"));

    verify(hub, never()).publishExceptUser(
      org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
      org.mockito.ArgumentMatchers.any());
  }
}
