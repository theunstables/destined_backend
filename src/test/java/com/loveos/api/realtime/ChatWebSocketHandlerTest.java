package com.loveos.api.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loveos.api.messaging.MessagingService;
import com.loveos.api.messaging.dto.MessagingResponses;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @BeforeEach
  void setUp() {
    hub = mock(RealtimeHub.class);
    messaging = mock(MessagingService.class);
    handler = new ChatWebSocketHandler(hub, messaging, JsonMapper.builder().build());
    session = mock(WebSocketSession.class);
    userId = UUID.randomUUID();
    coupleId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();
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
}
