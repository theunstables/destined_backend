package com.loveos.api.config;

import com.loveos.api.auth.JwtAuthenticationFilter;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.core.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the API's error envelope for failures that happen inside the security
 * filter chain, before any controller runs.
 *
 * <p>WITHOUT THIS, Spring Security answers with its own HTML error page. The Expo
 * client parses JSON, finds none, and reports {@code UNKNOWN} — so a perfectly
 * ordinary expired token would surface as an unexplained failure instead of
 * triggering the refresh-and-retry path that already exists.
 *
 * <p>The filter records *why* it could not authenticate; this class turns that
 * into the right code. {@code TOKEN_EXPIRED} and {@code TOKEN_INVALID} mean
 * different things to the client and must not be collapsed.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** No credentials, or credentials that did not verify. */
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {

    Object recorded = request.getAttribute(JwtAuthenticationFilter.FAILURE_ATTRIBUTE);

    ErrorCode code = ErrorCode.UNAUTHORIZED;
    String message = "Authentication required";

    if (recorded instanceof AppException failure) {
      code = failure.code();
      message = failure.getMessage();
    }

    write(response, code, message);
  }

  /**
   * Authenticated, but not permitted.
   *
   * <p>In practice this is an unverified email hitting a route that requires one.
   * Reported as {@code EMAIL_NOT_VERIFIED} rather than a bare 403, because the
   * app has a screen for exactly that state and needs to know to show it.
   */
  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      org.springframework.security.access.AccessDeniedException deniedException)
      throws IOException {

    write(response, ErrorCode.EMAIL_NOT_VERIFIED, "Verify your email to continue");
  }

  private void write(HttpServletResponse response, ErrorCode code, String message)
      throws IOException {
    response.setStatus(code.status());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, message));
  }
}
