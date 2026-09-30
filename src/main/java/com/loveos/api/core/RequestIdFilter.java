package com.loveos.api.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request an id, echoes it in {@code x-request-id}, and puts it in
 * the logging context.
 *
 * <p>Runs first — {@code HIGHEST_PRECEDENCE} — so that even a request rejected by
 * authentication carries an id. An error a user can quote but support cannot
 * find is worse than no id at all.
 *
 * <p>AN INBOUND ID IS ACCEPTED BUT NOT TRUSTED. A gateway that already assigned
 * one lets a single id span several services, which is the point. But the header
 * is attacker-controlled, so it is length-capped and character-restricted before
 * use: it ends up in log files and in a response header, and an unvalidated
 * value there is a log-injection and response-splitting vector. Anything that
 * fails the check is silently replaced rather than rejected — the caller's
 * tracing preference is not worth a failed request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "x-request-id";
  private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

  /** Deliberately narrow: what tracing systems emit, and nothing else. */
  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String inbound = request.getHeader(HEADER);
    String id = (inbound != null && SAFE.matcher(inbound).matches())
        ? inbound
        : UUID.randomUUID().toString();

    RequestId.set(id);
    MDC.put("requestId", id);
    long startedAt = System.nanoTime();
    // Set before the chain runs, so it is present even if a handler throws.
    response.setHeader(HEADER, id);

    try {
      chain.doFilter(request, response);
    } finally {
      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      log.info("request completed method={} path={} status={} durationMs={}",
          request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
      // Pooled threads outlive requests; a stale id would mislabel the next one.
      MDC.remove("requestId");
      RequestId.clear();
    }
  }
}
