package com.loveos.api.messaging;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import com.loveos.api.messaging.dto.MessagingRequests;
import com.loveos.api.messaging.dto.MessagingResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/chat")
public class MessagingController {
  private final MessagingService service;

  public MessagingController(MessagingService service) {
    this.service = service;
  }

  @GetMapping("/messages")
  public ApiResponse<List<MessagingResponses.MessageDto>> list(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
    var page = service.list(principal(principal).userId(), cursor, limit);
    return ApiResponse.of(page.items(), Collections.singletonMap("nextCursor", page.nextCursor()));
  }

  @GetMapping("/messages/pinned")
  public ApiResponse<List<MessagingResponses.MessageDto>> pinned(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.pinned(principal(principal).userId()));
  }

  @GetMapping("/messages/search")
  public ApiResponse<List<MessagingResponses.MessageDto>> search(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam @NotBlank @Size(max = 200) String q,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
    return ApiResponse.of(service.search(principal(principal).userId(), q, limit));
  }

  @PostMapping("/messages")
  public ResponseEntity<ApiResponse<MessagingResponses.MessageDto>> send(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody MessagingRequests.SendMessage input) {
    var result = service.send(principal(principal).userId(), input);
    return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED)
        .body(ApiResponse.of(result.message()));
  }

  @PostMapping("/messages/{id}/reactions")
  public ApiResponse<MessagingResponses.MessageDto> react(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String id,
      @Valid @RequestBody MessagingRequests.Reaction input) {
    return ApiResponse.of(service.toggleReaction(
        principal(principal).userId(), MessagingService.id(id), input.emoji()));
  }

  @PostMapping("/messages/{id}/pin")
  public ApiResponse<MessagingResponses.MessageDto> pin(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
    return ApiResponse.of(service.togglePin(
        principal(principal).userId(), MessagingService.id(id)));
  }

  @PostMapping("/read")
  public ApiResponse<MessagingResponses.ReadResult> read(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody MessagingRequests.ReadReceipt input) {
    return ApiResponse.of(service.markRead(
        principal(principal).userId(), MessagingService.id(input.upToMessageId())));
  }

  @DeleteMapping("/messages/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
    service.delete(principal(principal).userId(), MessagingService.id(id));
  }

  private static AuthenticatedUser principal(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal;
  }
}
