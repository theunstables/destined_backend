package com.loveos.api.dailyquestions;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/daily-question")
public class DailyQuestionController {

  private final DailyQuestionService service;

  public DailyQuestionController(DailyQuestionService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<DailyQuestionDtos.DailyQuestion> today(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(service.today(userId(principal)));
  }

  @PutMapping("/answer")
  public ApiResponse<DailyQuestionDtos.DailyQuestion> answer(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody DailyQuestionDtos.SubmitAnswer request) {
    return ApiResponse.of(service.answer(userId(principal), request));
  }

  private static java.util.UUID userId(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal.userId();
  }
}
