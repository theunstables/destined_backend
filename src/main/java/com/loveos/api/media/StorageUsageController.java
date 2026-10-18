package com.loveos.api.media;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/account/storage")
public class StorageUsageController {
  private final StorageUsageService service;

  public StorageUsageController(StorageUsageService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<StorageUsageService.Usage> get(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.get(principal.userId()));
  }
}
