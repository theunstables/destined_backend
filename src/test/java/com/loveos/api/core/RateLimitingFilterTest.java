package com.loveos.api.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RateLimitingFilterTest {

  @AfterEach
  void clearRequestId() {
    RequestId.clear();
  }

  @Test
  void eleventhLoginFromSameAddressReturnsContractual429() throws Exception {
    var filter = new RateLimitingFilter(
        JsonMapper.builder().build(),
        Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC));
    RequestId.set("phase0-test-request");

    for (int attempt = 1; attempt <= 10; attempt++) {
      MockHttpServletResponse response = invoke(filter, "/v1/auth/login", "192.0.2.1");
      assertThat(response.getStatus()).isEqualTo(200);
    }

    MockHttpServletResponse rejected = invoke(filter, "/v1/auth/login", "192.0.2.1");
    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.getHeader("Retry-After")).isEqualTo("900");
    assertThat(rejected.getContentAsString()).contains(
        "\"code\":\"RATE_LIMITED\"", "\"requestId\":\"phase0-test-request\"");
  }

  @Test
  void unrelatedAndNonPostRequestsAreNotLimited() throws Exception {
    var filter = new RateLimitingFilter(JsonMapper.builder().build());
    var chain = mock(jakarta.servlet.FilterChain.class);
    var request = new MockHttpServletRequest("GET", "/v1/auth/login");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  private static MockHttpServletResponse invoke(
      RateLimitingFilter filter, String path, String address) throws Exception {
    var request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr(address);
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
