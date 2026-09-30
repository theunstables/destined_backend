package com.loveos.api.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class RealtimeHubTest {
  @Test
  void routesPerUserWithoutCrossCoupleLeakage() throws Exception {
    RealtimeHub hub = new RealtimeHub(JsonMapper.builder().build());
    UUID coupleId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();
    WebSocketSession recipient = session("recipient");
    WebSocketSession partner = session("partner");
    WebSocketSession stranger = session("stranger");
    hub.register(recipient, recipientId, coupleId);
    hub.register(partner, UUID.randomUUID(), coupleId);
    hub.register(stranger, recipientId, UUID.randomUUID());

    hub.publishToUser(coupleId, recipientId, Map.of("type", "status", "status", "read"));

    verify(recipient).sendMessage(argThat(message -> message instanceof TextMessage text
        && text.getPayload().contains("\"status\":\"read\"")));
    verify(partner, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    verify(stranger, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    assertThat(hub.connectionCount()).isEqualTo(3);
  }

  @Test
  void heartbeatClosesConnectionThatMissesAProtocolPong() throws Exception {
    RealtimeHub hub = new RealtimeHub(JsonMapper.builder().build());
    WebSocketSession session = session("phone");
    hub.register(session, UUID.randomUUID(), UUID.randomUUID());

    hub.heartbeat();
    verify(session).sendMessage(org.mockito.ArgumentMatchers.any(PingMessage.class));
    hub.heartbeat();

    verify(session).close(CloseStatus.GOING_AWAY);
    assertThat(hub.connectionCount()).isZero();
  }

  private WebSocketSession session(String id) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    when(session.isOpen()).thenReturn(true);
    return session;
  }
}
