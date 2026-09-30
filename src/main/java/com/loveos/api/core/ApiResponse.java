package com.loveos.api.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * THE SUCCESS ENVELOPE — {@code { data, meta, requestId }}.
 *
 * <p>Every 2xx body in this API has this shape, because the client unwraps it in
 * exactly one place ({@code services/http/client.ts}) and treats anything else as
 * malformed. A handler that returns a bare object would fail there with a
 * confusing {@code UNKNOWN} rather than a useful error.
 *
 * <p>{@code data} is annotated {@code ALWAYS} on purpose. The client's check is
 * literally {@code 'data' in payload}, so a null payload must still serialise the
 * key — omitting it would make a successful empty response indistinguishable from
 * a broken one. {@code meta} and {@code requestId} are omitted when absent,
 * keeping responses small on mobile connections.
 *
 * <p>{@code meta} is a loose map rather than a typed record because its contents
 * are per-endpoint; today only {@code nextCursor} is contractual.
 */
public record ApiResponse<T>(
    @JsonInclude(JsonInclude.Include.ALWAYS) T data,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> meta,
    @JsonInclude(JsonInclude.Include.NON_NULL) String requestId) {

  public static <T> ApiResponse<T> of(T data) {
    return new ApiResponse<>(data, null, RequestId.current());
  }

  /** Used by list endpoints to carry {@code nextCursor}. */
  public static <T> ApiResponse<T> of(T data, Map<String, Object> meta) {
    return new ApiResponse<>(data, meta, RequestId.current());
  }
}
