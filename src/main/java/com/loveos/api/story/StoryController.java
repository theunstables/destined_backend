package com.loveos.api.story;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import com.loveos.api.story.dto.StoryRequests;
import com.loveos.api.story.dto.StoryResponses;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/story")
public class StoryController {

  private final StoryService storyService;

  public StoryController(StoryService storyService) {
    this.storyService = storyService;
  }

  @GetMapping
  public ApiResponse<StoryResponses.StoryDto> getStory(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ApiResponse.of(storyService.getStory(principal(principal).userId()));
  }

  @PutMapping
  public ApiResponse<StoryResponses.StoryDto> saveStory(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody StoryRequests.SaveStory request) {
    return ApiResponse.of(storyService.saveStory(principal(principal).userId(), request));
  }

  private static AuthenticatedUser principal(AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return principal;
  }
}