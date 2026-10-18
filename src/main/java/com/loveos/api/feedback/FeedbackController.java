package com.loveos.api.feedback;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/feedback")
public class FeedbackController {
  private final FeedbackService service;

  public FeedbackController(FeedbackService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<FeedbackService.Receipt> submit(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @RequestBody FeedbackService.Submission submission) {
    return ApiResponse.of(service.submit(principal.userId(), submission));
  }
}
