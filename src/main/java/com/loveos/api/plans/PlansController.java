package com.loveos.api.plans;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/v1/plans")
public class PlansController {
  private final PlansService plans;

  public PlansController(PlansService plans) {
    this.plans = plans;
  }

  @GetMapping
  public ApiResponse<ObjectNode> get(@AuthenticationPrincipal AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return ApiResponse.of(plans.get(principal.userId()));
  }

  @PutMapping
  public ApiResponse<ObjectNode> replace(
      @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody ObjectNode input) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return ApiResponse.of(plans.replace(principal.userId(), input));
  }
}