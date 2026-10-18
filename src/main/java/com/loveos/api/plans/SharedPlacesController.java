package com.loveos.api.plans;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/plans/places")
public class SharedPlacesController {
  private final SharedPlacesService service;

  public SharedPlacesController(SharedPlacesService service) {
    this.service = service;
  }

  @GetMapping
  ApiResponse<SharedPlacesService.PlacesView> get(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.get(userId(principal)));
  }

  @PutMapping("/me")
  ApiResponse<SharedPlacesService.PlacesView> share(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestBody SharedPlacesService.ShareRequest request) {
    return ApiResponse.of(service.share(userId(principal), request));
  }

  @DeleteMapping("/me")
  ApiResponse<SharedPlacesService.PlacesView> pause(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.pause(userId(principal)));
  }

  private static UUID userId(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal.userId();
  }
}
