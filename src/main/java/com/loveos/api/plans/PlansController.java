package com.loveos.api.plans;

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
@RequestMapping("/v1/plans")
public class PlansController {
  private final PlansService service;

  public PlansController(PlansService service) {
    this.service = service;
  }

  @GetMapping
  ApiResponse<JsonNode> get(@AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.get(userId(principal)));
  }

  @PutMapping
  ApiResponse<JsonNode> replace(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestBody JsonNode state) {
    return ApiResponse.of(service.replace(userId(principal), state));
  }

  private static UUID userId(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal.userId();
  }
}
