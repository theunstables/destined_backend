package com.loveos.api.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * THE FAILURE ENVELOPE — {@code { error: { code, message, issues }, requestId }}.
 *
 * <p>{@code code} is the contract; {@code message} is not. The client renders its
 * own copy from the code and uses the message only for logs, which is what lets
 * this server reword an error without an app release, and what stops an internal
 * detail from reaching a user.
 *
 * <p>{@code issues} is populated only for {@code VALIDATION_ERROR}, where knowing
 * *which* field failed is the entire value of the response.
 */
public record ErrorResponse(Body error, @JsonInclude(JsonInclude.Include.NON_NULL) String requestId) {

  public record Body(
      String code,
      String message,
      @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldIssue> issues) {}

  /** One field rejection. {@code field} is the client-facing property name. */
  public record FieldIssue(String field, String message) {}

  public static ErrorResponse of(ErrorCode code, String message) {
    return new ErrorResponse(new Body(code.name(), message, null), RequestId.current());
  }

  public static ErrorResponse of(ErrorCode code, String message, List<FieldIssue> issues) {
    // An empty list is noise; the client checks for the key's presence.
    List<FieldIssue> present = (issues == null || issues.isEmpty()) ? null : issues;
    return new ErrorResponse(new Body(code.name(), message, present), RequestId.current());
  }
}
