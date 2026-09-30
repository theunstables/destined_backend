package com.loveos.api.memories;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.memories.dto.MemoryRequests;
import com.loveos.api.memories.dto.MemoryResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Collections;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/v1/memories")
public class MemoriesController {

  private final MemoriesService service;

  public MemoriesController(MemoriesService service) {
    this.service = service;
  }

  @GetMapping("/albums")
  public ApiResponse<List<MemoryResponses.AlbumDto>> listAlbums(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.listAlbums(principal(principal).userId()));
  }

  @PostMapping("/albums")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<MemoryResponses.AlbumDto> createAlbum(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody MemoryRequests.CreateAlbum request) {
    return ApiResponse.of(service.createAlbum(principal(principal).userId(), request));
  }

  @GetMapping("/albums/{id}")
  public ApiResponse<MemoryResponses.AlbumDto> getAlbum(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
    return ApiResponse.of(service.getAlbum(principal(principal).userId(), id));
  }

  @PutMapping("/albums/{id}/memories/{memoryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void addToAlbum(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String id, @PathVariable String memoryId) {
    service.addToAlbum(principal(principal).userId(), id(id, "Unknown album"),
        id(memoryId, "Unknown memory"));
  }

  @DeleteMapping("/albums/{id}/memories/{memoryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeFromAlbum(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String id, @PathVariable String memoryId) {
    service.removeFromAlbum(principal(principal).userId(), id(id, "Unknown album"),
        id(memoryId, "Unknown memory"));
  }

  @GetMapping("/search")
  public ApiResponse<List<MemoryResponses.MemoryDto>> search(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam @NotBlank @Size(max = 200) String q,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
    return ApiResponse.of(service.search(principal(principal).userId(), q, limit));
  }

  @GetMapping
  public ApiResponse<List<MemoryResponses.MemoryDto>> list(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      @RequestParam(required = false) @Size(max = 40) String tag,
      @RequestParam(required = false) String albumId,
      @RequestParam(required = false) Boolean favorite,
      @RequestParam(required = false) @Pattern(regexp = "^\\d{2}-\\d{2}$") String onDay) {
    MemoryResponses.MemoryPage page = service.list(
        principal(principal).userId(), cursor, limit, tag, albumId, favorite, onDay);
    return ApiResponse.of(page.items(), Collections.singletonMap("nextCursor", page.nextCursor()));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<MemoryResponses.MemoryDto> create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody MemoryRequests.CreateMemory request) {
    return ApiResponse.of(service.create(principal(principal).userId(), request));
  }

  @GetMapping("/{id}")
  public ApiResponse<MemoryResponses.MemoryDto> get(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
    return ApiResponse.of(service.get(principal(principal).userId(), id(id, "Unknown memory")));
  }

  @PatchMapping("/{id}")
  public ApiResponse<MemoryResponses.MemoryDto> update(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String id,
      @Valid @RequestBody MemoryRequests.UpdateMemory request) {
    return ApiResponse.of(service.update(
        principal(principal).userId(), id(id, "Unknown memory"), request));
  }

  @PutMapping("/{id}/private-note")
  public ApiResponse<MemoryResponses.MemoryDto> upsertPrivateNote(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String id,
      @Valid @RequestBody MemoryRequests.UpsertPrivateNote request) {
    return ApiResponse.of(service.upsertPrivateNote(
        principal(principal).userId(), id(id, "Unknown memory"), request.note()));
  }

  @DeleteMapping("/{id}/private-note")
  public ApiResponse<MemoryResponses.MemoryDto> withdrawPrivateNote(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
    return ApiResponse.of(service.withdrawPrivateNote(
        principal(principal).userId(), id(id, "Unknown memory")));
  }

  @PostMapping("/{id}/favorite")
  public ApiResponse<MemoryResponses.MemoryDto> toggleFavorite(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
    return ApiResponse.of(service.toggleFavorite(
        principal(principal).userId(), id(id, "Unknown memory")));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
    service.delete(principal(principal).userId(), id(id, "Unknown memory"));
  }

  private static UUID id(String value, String message) {
    try { return UUID.fromString(value); }
    catch (IllegalArgumentException exception) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, message);
    }
  }

  private static AuthenticatedUser principal(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal;
  }
}