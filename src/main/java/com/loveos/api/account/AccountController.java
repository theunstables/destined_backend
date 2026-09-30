package com.loveos.api.account;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/account")
public class AccountController {

  private final AccountService service;

  public AccountController(AccountService service) {
    this.service = service;
  }

  @GetMapping("/deletion")
  ApiResponse<AccountService.DeletionStatus> deletionStatus(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.deletionStatus(userId(principal)));
  }

  @PostMapping("/deletion")
  ApiResponse<AccountService.DeletionStatus> requestDeletion(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.requestDeletion(userId(principal)));
  }

  @DeleteMapping("/deletion")
  ApiResponse<AccountService.DeletionStatus> cancelDeletion(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.cancelDeletion(userId(principal)));
  }

  @GetMapping("/export")
  ApiResponse<AccountService.AccountExport> export(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.export(userId(principal)));
  }

  private static UUID userId(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal.userId();
  }
}
