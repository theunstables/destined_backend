package com.loveos.api.timeline;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Collections;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/timeline")
public class TimelineController {

  private final TimelineService service;

  public TimelineController(TimelineService service) {
    this.service = service;
  }

  @GetMapping
  ApiResponse<java.util.List<TimelineService.Item>> list(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    TimelineService.Page page = service.list(principal.userId(), cursor, limit);
    return ApiResponse.of(
        page.items(), Collections.singletonMap("nextCursor", page.nextCursor()));
  }
}
