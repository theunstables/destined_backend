package com.loveos.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

class SecurityCorsConfigurationTest {

  @Test
  void trimsCommaSeparatedOriginsAndRemovesDuplicates() {
    var properties = new SecurityConfig.CorsProperties(List.of(
        "https://destinedfrontend-xi.vercel.app, "
            + "https://destinedfrontend-git-main-theunstables.vercel.app",
        "https://destinedfrontend-xi.vercel.app"));

    assertThat(properties.origins()).containsExactly(
        "https://destinedfrontend-xi.vercel.app",
        "https://destinedfrontend-git-main-theunstables.vercel.app");
  }

  @Test
  void acceptsHttpsAndLocalDevelopmentOrigins() {
    assertThat(new SecurityConfig.CorsProperties(List.of(
        "https://destinedfrontend-xi.vercel.app", "http://localhost:8081")).origins())
        .hasSize(2);
  }

  @Test
  void rejectsWildcardsPathsAndInsecureRemoteOrigins() {
    assertThatThrownBy(() -> new SecurityConfig.CorsProperties(List.of("https://*.vercel.app")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SecurityConfig.CorsProperties(List.of("https://example.com/app")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SecurityConfig.CorsProperties(List.of("http://example.com")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAnEmptyAllowList() {
    assertThatThrownBy(() -> new SecurityConfig.CorsProperties(List.of("  ", ",")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one CORS origin");
  }

  @Test
  void allowsConfiguredBrowserPreflightsAndRejectsUnknownOrigins() throws Exception {
    List<String> allowed = List.of(
        "https://destinedfrontend-xi.vercel.app",
        "https://destinedfrontend-git-main-theunstables.vercel.app",
        "https://destinedfrontend-qjrn01827-theunstables.vercel.app");
    var security = new SecurityConfig(null, null, null,
        new SecurityConfig.CorsProperties(allowed));
    var filter = new CorsFilter(security.corsConfigurationSource());

    for (String origin : allowed) {
      MockHttpServletResponse response = preflight(filter, origin);
      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
      assertThat(response.getHeader("Access-Control-Allow-Methods"))
          .contains("POST");
      assertThat(response.getHeader("Access-Control-Allow-Headers"))
          .containsIgnoringCase("authorization")
          .containsIgnoringCase("content-type");
    }

    assertThat(preflight(filter, "https://attacker.example").getStatus()).isEqualTo(403);
  }

  private static MockHttpServletResponse preflight(CorsFilter filter, String origin)
      throws Exception {
    var request = new MockHttpServletRequest("OPTIONS", "/v1/auth/login");
    request.addHeader("Origin", origin);
    request.addHeader("Access-Control-Request-Method", "POST");
    request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}