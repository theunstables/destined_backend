package com.loveos.api.repairsignal;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/repair-signal")
public class RepairSignalController {

  private final RepairSignalService service;

  public RepairSignalController(RepairSignalService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<RepairSignalDtos.Signal> current(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.current(userId(principal)));
  }

  @PostMapping
  public ApiResponse<RepairSignalDtos.Signal> send(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.send(userId(principal)));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@AuthenticationPrincipal AuthenticatedUser principal) {
    service.cancel(userId(principal));
  }

  private static UUID userId(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal.userId();
  }
}
