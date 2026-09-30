package com.loveos.api.pairing;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import com.loveos.api.pairing.dto.PairingRequests;
import com.loveos.api.pairing.dto.PairingResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/pairing")
public class PairingController {

  private final PairingService pairingService;

  public PairingController(PairingService pairingService) {
    this.pairingService = pairingService;
  }

  @GetMapping("/profile")
  public ApiResponse<PairingResponses.ProfileDto> getProfile(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(pairingService.getProfile(principal(principal).userId()));
  }

  @PutMapping("/profile")
  public ApiResponse<PairingResponses.ProfileDto> saveProfile(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody PairingRequests.ProfileUpdate request) {
    return ApiResponse.of(pairingService.saveProfile(principal(principal).userId(), request));
  }

  @PostMapping("/invites")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<PairingResponses.InviteDto> createInvite(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(pairingService.createInvite(principal(principal).userId()));
  }

  @PostMapping("/invites/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelInvite(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody PairingRequests.CancelInvite request) {
    pairingService.cancelInvite(principal(principal).userId(), request.code());
  }

  @PostMapping("/redeem")
  public ApiResponse<PairingResponses.PartnerDto> redeem(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody PairingRequests.RedeemCode request) {
    return ApiResponse.of(pairingService.redeemCode(principal(principal).userId(), request.code()));
  }

  @PostMapping("/confirm")
  public ApiResponse<PairingResponses.PartnerDto> confirm(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody PairingRequests.ConfirmPartner request) {
    return ApiResponse.of(
        pairingService.confirmPartner(principal(principal).userId(), request.partnerId()));
  }

  @GetMapping("/space")
  public ApiResponse<PairingResponses.SpaceDto> getSpace(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(pairingService.getSpace(principal(principal).userId()));
  }

  @PatchMapping("/space")
  public ApiResponse<PairingResponses.SpaceDto> updateSpace(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody PairingRequests.UpdateSpace request) {
    return ApiResponse.of(pairingService.updateSpace(principal(principal).userId(), request));
  }

  @GetMapping("/lifecycle")
  public ApiResponse<PairingResponses.LifecycleDto> getLifecycle(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(pairingService.getLifecycle(principal(principal).userId()));
  }

  @PostMapping("/lifecycle/pause")
  public ApiResponse<PairingResponses.LifecycleDto> pause(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(pairingService.pause(principal(principal).userId()));
  }

  @PostMapping("/lifecycle/reactivate")
  public ApiResponse<PairingResponses.LifecycleDto> reactivate(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(pairingService.reactivate(principal(principal).userId()));
  }

  @PostMapping("/lifecycle/unpair")
  public ApiResponse<PairingResponses.LifecycleDto> requestUnpair(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(pairingService.requestUnpair(principal(principal).userId()));
  }

  @PostMapping("/lifecycle/unpair/cancel")
  public ApiResponse<PairingResponses.LifecycleDto> cancelUnpair(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(pairingService.cancelUnpair(principal(principal).userId()));
  }

  private static AuthenticatedUser principal(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal;
  }
}