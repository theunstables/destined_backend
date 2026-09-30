package com.loveos.api.home;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import com.loveos.api.core.AppException;
import com.loveos.api.home.dto.HomeResponses;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/home")
public class HomeController {

  private final HomeService service;

  public HomeController(HomeService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<HomeResponses.HomeDto> getHome(
      @AuthenticationPrincipal AuthenticatedUser principal) {
    if (principal == null) throw AppException.unauthorized("Sign in to continue");
    return ApiResponse.of(service.getHome(principal.userId()));
  }
}