package com.loveos.api.realtime;

import com.loveos.api.auth.JwtService;
import com.loveos.api.core.AppException;
import com.loveos.api.pairing.CoupleAccess;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {
  static final String USER_ID = "loveos.userId";
  static final String COUPLE_ID = "loveos.coupleId";
  static final String PARTNER_ID = "loveos.partnerId";

  private final JwtService jwt;
  private final CoupleAccess couples;

  public RealtimeHandshakeInterceptor(JwtService jwt, CoupleAccess couples) {
    this.jwt = jwt;
    this.couples = couples;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attributes) {
    try {
      String token = token(request.getURI());
      if (token == null) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }
      JwtService.AccessClaims claims = jwt.verify(token);
      if (!claims.emailVerified()) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }
      CoupleAccess.Membership membership = couples.requireMembership(claims.userId());
      if (!membership.connected()) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }
      UUID partnerId = couples.members(claims.userId()).stream()
          .map(CoupleAccess.Member::userId)
          .filter(id -> !id.equals(claims.userId()))
            .findFirst()
            .orElseThrow(() -> new AppException(
              com.loveos.api.core.ErrorCode.FORBIDDEN, "Partner is not connected"));
      attributes.put(USER_ID, claims.userId());
      attributes.put(COUPLE_ID, membership.coupleId());
      attributes.put(PARTNER_ID, partnerId);
      return true;
    } catch (AppException exception) {
      response.setStatusCode(exception.code().status() == 401
          ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN);
      return false;
    } catch (RuntimeException exception) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {}

  private static String token(URI uri) {
    String query = uri.getRawQuery();
    if (query == null) return null;
    for (String pair : query.split("&")) {
      int separator = pair.indexOf('=');
      if (separator > 0 && pair.substring(0, separator).equals("token")) {
        String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
        return value.isBlank() ? null : value;
      }
    }
    return null;
  }
}
