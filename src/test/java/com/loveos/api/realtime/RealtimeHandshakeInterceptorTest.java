package com.loveos.api.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loveos.api.auth.JwtService;
import com.loveos.api.pairing.CoupleAccess;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class RealtimeHandshakeInterceptorTest {
  @Test
  void acceptsVerifiedConnectedUserAndBindsTrustedContext() {
    JwtService jwt = mock(JwtService.class);
    CoupleAccess couples = mock(CoupleAccess.class);
    UUID userId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();
    UUID coupleId = UUID.randomUUID();
    when(jwt.verify("valid-token"))
        .thenReturn(new JwtService.AccessClaims(userId, "alex@example.test", true));
    when(couples.requireMembership(userId))
        .thenReturn(new CoupleAccess.Membership(coupleId, CoupleAccess.Role.FOUNDER, true));
    when(couples.members(userId)).thenReturn(List.of(
        new CoupleAccess.Member(userId, CoupleAccess.Role.FOUNDER, "Alex", null),
        new CoupleAccess.Member(partnerId, CoupleAccess.Role.MEMBER, "Sam", null)));
    var interceptor = new RealtimeHandshakeInterceptor(jwt, couples);
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    when(request.getURI()).thenReturn(URI.create("http://localhost/ws?token=valid-token"));
    var attributes = new HashMap<String, Object>();

    assertThat(interceptor.beforeHandshake(
        request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes)).isTrue();
    assertThat(attributes).containsEntry(RealtimeHandshakeInterceptor.USER_ID, userId)
        .containsEntry(RealtimeHandshakeInterceptor.COUPLE_ID, coupleId)
        .containsEntry(RealtimeHandshakeInterceptor.PARTNER_ID, partnerId);
  }

  @Test
  void rejectsMissingTokenBeforeUpgrade() {
    JwtService jwt = mock(JwtService.class);
    var interceptor = new RealtimeHandshakeInterceptor(jwt, mock(CoupleAccess.class));
    ServerHttpRequest request = mock(ServerHttpRequest.class);
    ServerHttpResponse response = mock(ServerHttpResponse.class);
    when(request.getURI()).thenReturn(URI.create("http://localhost/ws"));

    assertThat(interceptor.beforeHandshake(
        request, response, mock(WebSocketHandler.class), new HashMap<>())).isFalse();
    verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
  }
}
