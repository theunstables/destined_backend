package com.loveos.api.config;

import com.loveos.api.auth.JwtAuthenticationFilter;
import com.loveos.api.core.RateLimitingFilter;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * THE SECURITY MODEL, IN ONE FILE.
 *
 * <p>Read the chain below top to bottom — the order is the policy, and a rule
 * placed wrongly is a hole rather than a style issue.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityConfig.CorsProperties.class)
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtFilter;
  private final RateLimitingFilter rateLimitingFilter;
  private final RestAuthenticationEntryPoint entryPoint;
  private final CorsProperties corsProperties;

  public SecurityConfig(
      JwtAuthenticationFilter jwtFilter,
      RateLimitingFilter rateLimitingFilter,
      RestAuthenticationEntryPoint entryPoint,
      CorsProperties corsProperties) {
    this.jwtFilter = jwtFilter;
    this.rateLimitingFilter = rateLimitingFilter;
    this.entryPoint = entryPoint;
    this.corsProperties = corsProperties;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        /*
         * CSRF IS DISABLED, AND THAT IS CORRECT HERE — it is not a shortcut.
         * CSRF exists because browsers attach cookies automatically. This API
         * authenticates with an Authorization header that a cross-site form
         * cannot set, so there is nothing to forge. Leaving it enabled would
         * break every POST from the mobile client for no security gain.
         *
         * This stops being true the moment authentication moves to a cookie.
         */
        .csrf(csrf -> csrf.disable())

        /*
         * STATELESS. No HttpSession is created, ever. Two reasons: the client is
         * a mobile app with no cookie jar, and a session would be server-side
         * state that prevents running more than one instance without sticky
         * routing or a shared session store.
         */
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // Both point at the same component so every rejection inside the chain
        // produces the API's JSON envelope rather than an HTML error page.
        .exceptionHandling(handling -> handling
            .authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(entryPoint))

        .authorizeHttpRequests(authorize -> authorize
            // Pre-flight must never require credentials — the browser sends none.
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

            // Shallow and unauthenticated, for load-balancer probes.
            .requestMatchers("/health", "/health/ready", "/actuator/health").permitAll()

            // Browser WebSocket clients cannot set Authorization. The upgrade
            // interceptor verifies the short-lived access token from `?token=`.
            .requestMatchers("/ws").permitAll()

            // Stored objects use unguessable UUID keys and must be fetchable by
            // native image/video components, which cannot attach bearer headers.
            .requestMatchers(HttpMethod.GET, "/media/**").permitAll()

            /*
             * The public auth surface. Every one of these is reachable by
             * definition without a token — you cannot present a token to obtain
             * your first token. Each is rate-limited instead.
             */
            .requestMatchers(
                "/v1/auth/signup",
                "/v1/auth/login",
                "/v1/auth/refresh",
                "/v1/auth/logout",
                "/v1/auth/password/forgot",
                "/v1/auth/password/reset",
                "/v1/auth/verify-email",
                "/v1/auth/verify-email/resend")
            .permitAll()

            /*
             * `/v1/auth/me` requires a token but NOT a verified email: the app
             * calls it to discover that verification is still outstanding.
             * Requiring verification here would make that state undiscoverable.
             */
            .requestMatchers("/v1/auth/me").authenticated()

            /*
             * DENY BY DEFAULT. Everything not named above needs a confirmed
             * email. Written as a catch-all so that a route added later is
             * protected by omission rather than exposed by it — the failure mode
             * of forgetting is a locked door, not an open one.
             */
            .anyRequest().hasAuthority(JwtAuthenticationFilter.ROLE_VERIFIED))

        // Before the username/password filter, which is unused but still present.
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

        // Register JWT's position first, then put rate limiting ahead of it.
        // RequestIdFilter runs outside this chain and has already supplied the
        // id used by a 429 response.
        .addFilterBefore(rateLimitingFilter, JwtAuthenticationFilter.class);

    return http.build();
  }

  /**
   * bcrypt at strength 12.
   *
   * <p>Above Spring's default of 10 — roughly four times the work per attempt,
   * which costs a legitimate sign-in a few tens of milliseconds and costs an
   * offline cracker four times as much for every guess. The Node implementation
   * used scrypt; bcrypt is chosen here because it ships with Spring Security and
   * needs no additional dependency. Both are deliberately slow, which is the
   * property that matters.
   *
   * <p>The strength is encoded in the hash itself, so this can be raised later
   * and existing hashes keep verifying.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  /**
   * These filters belong exclusively to the SecurityFilterChain. Because they
   * are also Spring components, Boot would otherwise register each directly
   * with the servlet container and execute it once outside the security chain.
   */
  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
      JwtAuthenticationFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  public FilterRegistrationBean<RateLimitingFilter> rateLimitFilterRegistration(
      RateLimitingFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  /**
   * An explicit allow-list, never a wildcard.
   *
   * <p>{@code allowCredentials} with {@code "*"} is rejected by every browser, and
   * reflecting the request's origin to work around that defeats the entire
   * mechanism. Native clients send no {@code Origin} header at all and are
   * unaffected by any of this — CORS is a browser control.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.origins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "x-request-id"));
    configuration.setExposedHeaders(List.of("x-request-id"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @ConfigurationProperties(prefix = "loveos.cors")
  public record CorsProperties(List<String> origins) {}
}
