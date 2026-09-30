package com.loveos.api.realtime;

import com.loveos.api.config.SecurityConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
public class RealtimeWebSocketConfig implements WebSocketConfigurer {
  private final ChatWebSocketHandler handler;
  private final RealtimeHandshakeInterceptor handshake;
  private final SecurityConfig.CorsProperties cors;

  public RealtimeWebSocketConfig(
      ChatWebSocketHandler handler,
      RealtimeHandshakeInterceptor handshake,
      SecurityConfig.CorsProperties cors) {
    this.handler = handler;
    this.handshake = handshake;
    this.cors = cors;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws")
        .addInterceptors(handshake)
        .setAllowedOrigins(cors.origins().toArray(String[]::new));
  }
}
