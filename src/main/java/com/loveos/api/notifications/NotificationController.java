package com.loveos.api.notifications;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping("/preferences")
  ApiResponse<NotificationDtos.Preferences> getPreferences(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.getPreferences(userId(principal)));
  }

  @PutMapping("/preferences")
  ApiResponse<NotificationDtos.Preferences> updatePreferences(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody NotificationDtos.UpdatePreferences request) {
    return ApiResponse.of(service.updatePreferences(userId(principal), request));
  }

  @PostMapping("/devices")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<NotificationDtos.Device> register(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody NotificationDtos.RegisterDevice request) {
    return ApiResponse.of(service.register(userId(principal), request));
  }

  @DeleteMapping("/devices")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void unregister(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody NotificationDtos.UnregisterDevice request) {
    service.unregister(userId(principal), request);
  }

  private static UUID userId(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal.userId();
  }
}
