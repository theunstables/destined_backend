package com.loveos.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loveos.api.core.AppException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void verifiedTokenCreatesPrincipalAndBothAuthorities() throws Exception {
    JwtService jwtService = mock(JwtService.class);
    UUID userId = UUID.randomUUID();
    when(jwtService.verify("access-token"))
        .thenReturn(new JwtService.AccessClaims(userId, "sam@example.com", true));
    var filter = new JwtAuthenticationFilter(jwtService);
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer access-token");
    var response = new MockHttpServletResponse();
    var chain = mock(jakarta.servlet.FilterChain.class);
    var observed = new AtomicReference<org.springframework.security.core.Authentication>();
    doAnswer(call -> {
      observed.set(SecurityContextHolder.getContext().getAuthentication());
      return null;
    }).when(chain).doFilter(request, response);

    filter.doFilter(request, response, chain);

    var authentication = observed.get();
    assertThat(authentication.getPrincipal())
        .isEqualTo(new AuthenticatedUser(userId, "sam@example.com", true));
    assertThat(authentication.getAuthorities()).extracting("authority")
        .containsExactlyInAnyOrder("ROLE_USER", "ROLE_VERIFIED");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void invalidTokenLeavesContextEmptyAndRecordsContractFailure() throws Exception {
    JwtService jwtService = mock(JwtService.class);
    AppException failure = AppException.tokenInvalid("bad token");
    when(jwtService.verify("bad-token")).thenThrow(failure);
    var filter = new JwtAuthenticationFilter(jwtService);
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer bad-token");

    filter.doFilter(request, new MockHttpServletResponse(), mock(jakarta.servlet.FilterChain.class));

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(request.getAttribute(JwtAuthenticationFilter.FAILURE_ATTRIBUTE)).isSameAs(failure);
  }
}
