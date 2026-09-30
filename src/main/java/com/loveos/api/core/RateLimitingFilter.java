package com.loveos.api.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Small, dependency-free fixed-window limiter for public credential endpoints.
 *
 * <p>This protects one application instance from casual brute force and email
 * abuse. It is intentionally not presented as a distributed limit: when LoveOS
 * runs multiple instances this implementation must be replaced by a shared
 * Redis/gateway limiter while preserving the same HTTP contract.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

  private static final Map<String, Policy> POLICIES = Map.of(
      "/v1/auth/signup", new Policy(5, Duration.ofMinutes(15)),
      "/v1/auth/login", new Policy(10, Duration.ofMinutes(15)),
      "/v1/auth/password/forgot", new Policy(3, Duration.ofHours(1)),
      "/v1/auth/verify-email/resend", new Policy(5, Duration.ofHours(1)),
      "/v1/pairing/invites", new Policy(10, Duration.ofMinutes(15)),
      "/v1/pairing/redeem", new Policy(10, Duration.ofMinutes(15)),
      "/v1/pairing/confirm", new Policy(10, Duration.ofMinutes(15)));

  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final ConcurrentHashMap<Key, Window> windows = new ConcurrentHashMap<>();
  private final AtomicLong requests = new AtomicLong();

  @Autowired
  public RateLimitingFilter(ObjectMapper objectMapper) {
    this(objectMapper, Clock.systemUTC());
  }

  RateLimitingFilter(ObjectMapper objectMapper, Clock clock) {
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equalsIgnoreCase(request.getMethod())
        || !POLICIES.containsKey(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    Policy policy = POLICIES.get(request.getRequestURI());
    Instant now = clock.instant();
    Key key = new Key(request.getRequestURI(), clientAddress(request));

    Window window = windows.compute(key, (ignored, current) -> {
      if (current == null || !now.isBefore(current.endsAt())) {
        return new Window(1, now.plus(policy.window()));
      }
      return new Window(current.count() + 1, current.endsAt());
    });

    // Bound stale-entry growth without a scheduler or background thread.
    if ((requests.incrementAndGet() & 1023) == 0) {
      windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().endsAt()));
    }

    if (window.count() > policy.limit()) {
      long retryAfter = Math.max(1, Duration.between(now, window.endsAt()).toSeconds());
      response.setStatus(ErrorCode.RATE_LIMITED.status());
      response.setHeader("Retry-After", Long.toString(retryAfter));
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(
          response.getOutputStream(),
          ErrorResponse.of(ErrorCode.RATE_LIMITED, "Too many requests; try again later"));
      return;
    }

    chain.doFilter(request, response);
  }

  private static String clientAddress(HttpServletRequest request) {
    String address = request.getRemoteAddr();
    return address == null || address.isBlank() ? "unknown" : address;
  }

  private record Policy(long limit, Duration window) {}

  private record Key(String path, String clientAddress) {}

  private record Window(long count, Instant endsAt) {}
}
