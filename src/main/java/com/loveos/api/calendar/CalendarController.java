package com.loveos.api.calendar;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.calendar.dto.CalendarRequests;
import com.loveos.api.calendar.dto.CalendarResponses;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/calendar")
public class CalendarController {

  private final CalendarService service;

  public CalendarController(CalendarService service) {
    this.service = service;
  }

  @GetMapping("/upcoming")
  public ApiResponse<List<CalendarResponses.ComingUpDto>> upcoming(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(defaultValue = "365") @Min(1) @Max(400) int withinDays,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
    return ApiResponse.of(service.upcoming(principal(principal).userId(), withinDays, limit));
  }

  @GetMapping
  public ApiResponse<List<CalendarResponses.EventDto>> list(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(required = false)
      @Pattern(regexp = CalendarRequests.DATE, message = "Date must be YYYY-MM-DD") String from,
      @RequestParam(required = false)
      @Pattern(regexp = CalendarRequests.DATE, message = "Date must be YYYY-MM-DD") String to,
      @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
    return ApiResponse.of(service.list(principal(principal).userId(), from, to, limit));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<CalendarResponses.EventDto> create(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody CalendarRequests.CreateEvent request) {
    return ApiResponse.of(service.create(principal(principal).userId(), request));
  }

  @GetMapping("/{id}")
  public ApiResponse<CalendarResponses.EventDto> get(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
    return ApiResponse.of(service.get(principal(principal).userId(), id(id)));
  }

  @PatchMapping("/{id}")
  public ApiResponse<CalendarResponses.EventDto> update(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @PathVariable String id,
      @Valid @RequestBody CalendarRequests.UpdateEvent request) {
    return ApiResponse.of(service.update(principal(principal).userId(), id(id), request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable String id) {
    service.delete(principal(principal).userId(), id(id));
  }

  private static UUID id(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Unknown event");
    }
  }

  private static AuthenticatedUser principal(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal;
  }
}