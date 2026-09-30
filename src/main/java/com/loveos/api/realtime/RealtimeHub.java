package com.loveos.api.realtime;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/** In-process couple socket registry. Scale-out replaces this boundary with pub/sub. */
@Component
public class RealtimeHub {
  private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);
  private final ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<>();
  private final ObjectMapper mapper;

  public RealtimeHub(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void register(WebSocketSession session, UUID userId, UUID coupleId) {
    connections.put(session.getId(),
        new Connection(session, userId, coupleId, new AtomicBoolean(true)));
  }

  public void markAlive(WebSocketSession session) {
    Connection connection = connections.get(session.getId());
    if (connection != null) connection.alive().set(true);
  }

  public void unregister(WebSocketSession session) {
    connections.remove(session.getId());
  }

  public boolean online(UUID coupleId, UUID userId) {
    return connections.values().stream().anyMatch(connection ->
        connection.coupleId().equals(coupleId) && connection.userId().equals(userId)
            && connection.session().isOpen());
  }

  public void publishToUser(UUID coupleId, UUID userId, Object event) {
    publish(coupleId, event, userId, null);
  }

  public void publishExceptUser(UUID coupleId, UUID excludedUserId, Object event) {
    publish(coupleId, event, null, excludedUserId);
  }

  public void publishToCouple(UUID coupleId, Object event) {
    publish(coupleId, event, null, null);
  }

  public int connectionCount() {
    return connections.size();
  }

  @Scheduled(fixedDelay = 30000)
  void heartbeat() {
    for (Connection connection : List.copyOf(connections.values())) {
      WebSocketSession session = connection.session();
      if (!session.isOpen()) {
        connections.remove(session.getId());
        continue;
      }
      try {
        if (!connection.alive().getAndSet(false)) {
          session.close(CloseStatus.GOING_AWAY);
          connections.remove(session.getId());
        } else {
          synchronized (session) {
            session.sendMessage(new PingMessage());
          }
        }
      } catch (IOException exception) {
        connections.remove(session.getId());
        log.debug("WebSocket heartbeat failed for session {}", session.getId(), exception);
      }
    }
  }

  private void publish(UUID coupleId, Object event, UUID onlyUserId, UUID excludedUserId) {
    final String payload;
    try {
      payload = mapper.writeValueAsString(event);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Realtime event is not serializable", exception);
    }

    for (Connection connection : List.copyOf(connections.values())) {
      if (!connection.coupleId().equals(coupleId)) continue;
      if (onlyUserId != null && !connection.userId().equals(onlyUserId)) continue;
      if (excludedUserId != null && connection.userId().equals(excludedUserId)) continue;
      WebSocketSession session = connection.session();
      if (!session.isOpen()) continue;
      try {
        synchronized (session) {
          session.sendMessage(new TextMessage(payload));
        }
      } catch (IOException exception) {
        log.warn("WebSocket send failed for session {}", session.getId(), exception);
      }
    }
  }

  private record Connection(
      WebSocketSession session, UUID userId, UUID coupleId, AtomicBoolean alive) {}
}
