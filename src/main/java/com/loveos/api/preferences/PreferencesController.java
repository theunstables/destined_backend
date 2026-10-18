package com.loveos.api.preferences;

import tools.jackson.databind.JsonNode;
import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/preferences")
public class PreferencesController {
  private final PreferencesService service;

  public PreferencesController(PreferencesService service) {
    this.service = service;
  }

  @GetMapping
  ApiResponse<JsonNode> get(@AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.get(userId(principal)));
  }

  @PutMapping
  ApiResponse<JsonNode> replace(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestBody JsonNode preferences) {
    return ApiResponse.of(service.replace(userId(principal), preferences));
  }

  private static UUID userId(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal.userId();
  }
}
