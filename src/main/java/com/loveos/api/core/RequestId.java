package com.loveos.api.core;

/**
 * The current request's correlation id.
 *
 * <p>Held in a {@link ThreadLocal} so the envelope factories can reach it without
 * every service signature growing an id parameter it does not otherwise use.
 * Spring MVC serves a request on one thread, so this is safe here — but it does
 * not propagate across {@code @Async} boundaries or reactive schedulers. If work
 * is ever moved onto another thread, the id must be passed explicitly rather
 * than read from here and silently found empty.
 *
 * <p>{@link RequestIdFilter} is the only writer, and it always clears the value
 * in a {@code finally} block: a value left behind would be inherited by the next
 * request on that pooled thread, quietly attaching the wrong id to somebody
 * else's log lines.
 */
public final class RequestId {

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private RequestId() {}

  static void set(String value) {
    CURRENT.set(value);
  }

  static void clear() {
    CURRENT.remove();
  }

  /** Null outside a request — during startup, or on a background thread. */
  public static String current() {
    return CURRENT.get();
  }
}
