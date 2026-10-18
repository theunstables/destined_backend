package com.loveos.api.realtime;

import com.loveos.api.messaging.MessagingService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
  private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
  private static final Set<String> PLAYBACK_ACTIONS = Set.of("play", "pause", "seek");
  private static final Set<String> WATCH_REACTIONS = Set.of("❤️", "😂", "😮", "😊");
  private static final Set<String> CALL_ACTIONS = Set.of("invite", "accept", "decline", "end");
  private static final Set<String> CALL_MODES = Set.of("voice", "video");

  private final RealtimeHub hub;
  private final MessagingService messaging;
  private final ObjectMapper mapper;

  public ChatWebSocketHandler(
      RealtimeHub hub, MessagingService messaging, ObjectMapper mapper) {
    this.hub = hub;
    this.messaging = messaging;
    this.mapper = mapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    UUID userId = attribute(session, RealtimeHandshakeInterceptor.USER_ID);
    UUID coupleId = attribute(session, RealtimeHandshakeInterceptor.COUPLE_ID);
    hub.register(session, userId, coupleId);

    messaging.markDelivered(userId);
    hub.publishExceptUser(coupleId, userId,
        Map.of("type", "presence", "userId", userId.toString(), "online", true));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage frame) {
    final Map<String, Object> event;
    try {
      event = mapper.readValue(frame.getPayload(), MAP);
    } catch (Exception exception) {
      return;
    }

    UUID userId = attribute(session, RealtimeHandshakeInterceptor.USER_ID);
    UUID coupleId = attribute(session, RealtimeHandshakeInterceptor.COUPLE_ID);
    Object type = event.get("type");

    if ("ping".equals(type)) {
      send(session, Map.of("type", "pong"));
      return;
    }
    if ("typing".equals(type) && event.get("isTyping") instanceof Boolean isTyping) {
      hub.publishExceptUser(coupleId, userId,
          Map.of("type", "typing", "isTyping", isTyping, "from", userId.toString()));
      return;
    }
    if ("watch:join".equals(type)) {
      UUID partnerId = attribute(session, RealtimeHandshakeInterceptor.PARTNER_ID);
      boolean connected = hub.online(coupleId, partnerId);
      send(session, Map.of("type", "watch:presence", "connected", connected));
      hub.publishExceptUser(coupleId, userId,
          Map.of("type", "watch:presence", "connected", true));
      return;
    }
    if ("watch:leave".equals(type)) {
      hub.publishExceptUser(coupleId, userId,
          Map.of("type", "watch:presence", "connected", false));
      return;
    }
    if ("watch:playback".equals(type)
        && event.get("action") instanceof String action
        && PLAYBACK_ACTIONS.contains(action)
        && number(event.get("positionSeconds")) instanceof Double position
        && position >= 0 && position <= 86_400) {
      hub.publishExceptUser(coupleId, userId, Map.of(
          "type", "watch:playback", "action", action,
          "positionSeconds", position, "sentAt", System.currentTimeMillis()));
      return;
    }
    if ("watch:whisper".equals(type) && event.get("text") instanceof String rawText) {
      String text = rawText.trim();
      if (!text.isEmpty() && text.length() <= 500) {
        double position = number(event.get("positionSeconds")) instanceof Double value
            ? Math.max(0, Math.min(value, 86_400)) : 0;
        hub.publishExceptUser(coupleId, userId, Map.of(
            "type", "watch:whisper", "id", UUID.randomUUID().toString(),
            "from", "partner", "text", text, "positionSeconds", position));
      }
      return;
    }
    if ("watch:reaction".equals(type)
        && event.get("emoji") instanceof String emoji
        && WATCH_REACTIONS.contains(emoji)) {
      hub.publishExceptUser(coupleId, userId,
          Map.of("type", "watch:reaction", "emoji", emoji));
      return;
    }
    if ("call:signal".equals(type)
        && event.get("callId") instanceof String callId
        && event.get("action") instanceof String action
        && event.get("mode") instanceof String mode
        && callId.matches("[A-Za-z0-9-]{8,80}")
        && CALL_ACTIONS.contains(action)
        && CALL_MODES.contains(mode)) {
      hub.publishExceptUser(coupleId, userId, Map.of(
          "type", "call:signal", "callId", callId, "action", action,
          "mode", mode, "from", userId.toString(), "sentAt", System.currentTimeMillis()));
      return;
    }
    if ("read".equals(type) && event.get("upToMessageId") instanceof String messageId) {
      try {
        messaging.markRead(userId, MessagingService.id(messageId));
      } catch (RuntimeException exception) {
        log.debug("Ignoring invalid WebSocket read receipt", exception);
      }
    }
  }

  private static Double number(Object value) {
    return value instanceof Number number ? number.doubleValue() : null;
  }

  @Override
  protected void handlePongMessage(WebSocketSession session, PongMessage message) {
    hub.markAlive(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    UUID userId = attribute(session, RealtimeHandshakeInterceptor.USER_ID);
    UUID coupleId = attribute(session, RealtimeHandshakeInterceptor.COUPLE_ID);
    hub.unregister(session);
    if (!hub.online(coupleId, userId)) {
      hub.publishExceptUser(coupleId, userId,
          Map.of("type", "presence", "userId", userId.toString(), "online", false));
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
    log.warn("WebSocket transport error for session {}", session.getId(), exception);
    if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
  }

  private void send(WebSocketSession session, Object event) {
    try {
      synchronized (session) {
        session.sendMessage(new TextMessage(mapper.writeValueAsString(event)));
      }
    } catch (Exception exception) {
      log.warn("WebSocket response failed for session {}", session.getId(), exception);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T attribute(WebSocketSession session, String name) {
    Object value = session.getAttributes().get(name);
    if (value == null) throw new IllegalStateException("Missing authenticated WebSocket context");
    return (T) value;
  }
}
