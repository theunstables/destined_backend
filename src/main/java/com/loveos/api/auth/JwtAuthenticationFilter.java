package com.loveos.api.auth;

import com.loveos.api.core.AppException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a {@code Authorization: Bearer …} header into an authenticated principal.
 *
 * <p>A MALFORMED OR EXPIRED TOKEN DOES NOT FAIL HERE. The filter leaves the
 * context empty and lets the request continue; the filter chain then rejects it
 * at the authorisation step, and {@link RestAuthenticationEntryPoint} writes the
 * envelope. Throwing from inside a filter would bypass
 * {@code @RestControllerAdvice} entirely and produce Spring's default HTML error
 * page — which the client cannot parse, and which would surface as
 * {@code UNKNOWN} instead of {@code TOKEN_EXPIRED}.
 *
 * <p>The reason for the failure is recorded on the request so the entry point can
 * distinguish "expired, please refresh" from "invalid, please sign in". Losing
 * that distinction would make every routine expiry look like a session loss.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** Request attribute carrying why authentication failed, for the entry point. */
  public static final String FAILURE_ATTRIBUTE = "loveos.auth.failure";

  /** Granted to a user whose email is confirmed; routes requiring it check for this. */
  public static final String ROLE_VERIFIED = "ROLE_VERIFIED";

  private static final String ROLE_USER = "ROLE_USER";

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String token = bearerToken(request);

    if (token != null) {
      try {
        JwtService.AccessClaims claims = jwtService.verify(token);

        // A verified user gets both authorities; an unverified one gets only
        // ROLE_USER, so `hasAuthority(ROLE_VERIFIED)` is all a route needs.
        List<SimpleGrantedAuthority> authorities = claims.emailVerified()
            ? List.of(new SimpleGrantedAuthority(ROLE_USER),
                      new SimpleGrantedAuthority(ROLE_VERIFIED))
            : List.of(new SimpleGrantedAuthority(ROLE_USER));

        var authentication = new UsernamePasswordAuthenticationToken(
            new AuthenticatedUser(claims.userId(), claims.email(), claims.emailVerified()),
            null,
            authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

      } catch (AppException failure) {
        // Recorded, not thrown. See the class note.
        request.setAttribute(FAILURE_ATTRIBUTE, failure);
        SecurityContextHolder.clearContext();
      }
    }

    try {
      chain.doFilter(request, response);
    } finally {
      // The context is thread-bound and threads are pooled; a leftover principal
      // would authenticate the next request on this thread as the wrong person.
      SecurityContextHolder.clearContext();
    }
  }

  private static String bearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null) return null;

    // Case-insensitive scheme, per RFC 7235.
    if (header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;

    String value = header.substring(7).trim();
    return value.isEmpty() ? null : value;
  }
}
