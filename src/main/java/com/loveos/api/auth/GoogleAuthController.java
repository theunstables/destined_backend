package com.loveos.api.auth;

import com.loveos.api.auth.dto.AuthResponses;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth/google")
public class GoogleAuthController {
  private final GoogleAuthService google;
  private final TokenHasher tokens;

  public GoogleAuthController(GoogleAuthService google, TokenHasher tokens) {
    this.google = google;
    this.tokens = tokens;
  }

  @GetMapping("/config")
  public ApiResponse<Map<String, Boolean>> config() {
    return ApiResponse.of(Map.of("enabled", google.enabled()));
  }

  @PostMapping("/start")
  public ResponseEntity<ApiResponse<GoogleAuthService.Start>> start(@Valid @RequestBody StartRequest request,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    if (request.link() && (principal == null || !principal.emailVerified())) throw AppException.unauthorized("Sign into your verified account first");
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.of(google.start(request.platform(), request.link() ? principal.userId() : null)));
  }

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(@RequestParam @Size(max = 256) String state,
      @RequestParam(required = false) String code, @RequestParam(required = false) String error) {
    return ResponseEntity.status(302).location(google.callback(state, code, error))
        .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").build();
  }

  @PostMapping("/exchange")
  public ResponseEntity<ApiResponse<AuthResponses.SessionDto>> exchange(@Valid @RequestBody ExchangeRequest request, HttpServletRequest http) {
    String userAgent = http.getHeader("User-Agent");
    if (userAgent != null && userAgent.length() > 256) userAgent = userAgent.substring(0, 256);
    var session = google.exchange(request.ticket(), request.exchangeSecret(),
        new AuthService.DeviceContext(userAgent, tokens.hash(http.getRemoteAddr())));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.of(session));
  }

  public record StartRequest(@NotBlank @Pattern(regexp = "web|native") String platform, boolean link) {}
  public record ExchangeRequest(@NotBlank @Size(max = 256) String ticket, @NotBlank @Size(max = 256) String exchangeSecret) {}
}