package com.loveos.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.loveos.api.config.AppProperties;
import com.loveos.api.core.AppException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class GoogleAuthServiceTest {
  private final TokenHasher tokens = new TokenHasher();
  private Map<String, GoogleAuthFlow> stored;
  private GoogleAuthFlowRepository repository;
  private GoogleTokenVerifier verifier;
  private AuthService auth;
  private GoogleAuthService google;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    stored = new HashMap<>();
    repository = mock(GoogleAuthFlowRepository.class);
    verifier = mock(GoogleTokenVerifier.class);
    auth = mock(AuthService.class);
    when(repository.save(any())).thenAnswer(call -> {
      GoogleAuthFlow flow = call.getArgument(0);
      stored.put(flow.getStateHash(), flow);
      return flow;
    });
    when(repository.findByStateHash(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
    when(repository.findByTicketHash(anyString())).thenAnswer(call -> stored.values().stream()
        .filter(flow -> call.getArgument(0).equals(flow.getTicketHash())).findFirst());
    doAnswer(call -> { stored.remove(((GoogleAuthFlow) call.getArgument(0)).getStateHash()); return null; })
        .when(repository).delete(any(GoogleAuthFlow.class));
    var builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    google = new GoogleAuthService(repository, verifier, auth, tokens,
        new AppProperties(null, null, null, "https://api.example.test"), "client-id", "server-only-secret",
        "https://app.example.test", "talesoftwo://google-callback", builder);
  }

  @Test
  void startUsesPkceNonceAndOnlyConfiguredRedirects() {
    var start = google.start("web", null);
    var query = UriComponentsBuilder.fromUriString(start.authorizationUrl()).build().getQueryParams();
    GoogleAuthFlow flow = stored.get(tokens.hash(start.state()));
    assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
    assertThat(query.getFirst("code_challenge")).hasSize(43);
    assertThat(query.getFirst("nonce")).isEqualTo(flow.getNonce());
    assertThat(start.redirectUrl()).isEqualTo("https://app.example.test/google-callback");
    assertThat(start.authorizationUrl()).doesNotContain(start.exchangeSecret(), "server-only-secret");
    assertThat(flow.getExchangeSecretHash()).isEqualTo(tokens.hash(start.exchangeSecret()));
    assertThat(google.start("native", null).redirectUrl()).isEqualTo("talesoftwo://google-callback");
  }

  @Test
  void validCallbackCanOnlyBeExchangedOnceByTheInitiatingClient() {
    var start = google.start("web", null);
    String ticket = complete(start);
    assertThatThrownBy(() -> google.exchange(ticket, "wrong-secret", device())).isInstanceOf(AppException.class);
    verifyNoInteractions(auth);
    google.exchange(ticket, start.exchangeSecret(), device());
    verify(auth).googleSignIn(any(GoogleTokenVerifier.Identity.class), isNull(), any());
    assertThatThrownBy(() -> google.exchange(ticket, start.exchangeSecret(), device())).isInstanceOf(AppException.class);
    server.verify();
  }

  @Test
  void expiredStateAndRepeatedCallbackAreRejected() {
    var expired = google.start("web", null);
    stored.get(tokens.hash(expired.state())).setExpiresAt(Instant.EPOCH);
    assertThatThrownBy(() -> google.callback(expired.state(), "code", null)).isInstanceOf(AppException.class);
    var current = google.start("web", null);
    complete(current);
    assertThatThrownBy(() -> google.callback(current.state(), "code", null)).isInstanceOf(AppException.class);
    server.verify();
  }

  @Test
  void cancellationReturnsNoCredentialsAndDoesNotContactGoogle() {
    var start = google.start("web", null);
    URI redirect = google.callback(start.state(), null, "access_denied");
    assertThat(redirect.toString()).startsWith(start.redirectUrl()).contains("error=cancelled")
        .doesNotContain("ticket=", start.exchangeSecret());
    verifyNoInteractions(verifier, auth);
    server.verify();
  }

  @Test
  void expiredExchangeTicketCannotCreateASession() {
    var start = google.start("web", null);
    String ticket = complete(start);
    stored.get(tokens.hash(start.state())).setExpiresAt(Instant.EPOCH);
    assertThatThrownBy(() -> google.exchange(ticket, start.exchangeSecret(), device())).isInstanceOf(AppException.class);
    verifyNoInteractions(auth);
    server.verify();
  }

  private String complete(GoogleAuthService.Start start) {
    server.expect(requestTo("https://oauth2.googleapis.com/token")).andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"id_token\":\"signed-google-token\"}", MediaType.APPLICATION_JSON));
    when(verifier.verify("signed-google-token", stored.get(tokens.hash(start.state())).getNonce()))
        .thenReturn(new GoogleTokenVerifier.Identity("google-subject", "qa@example.test"));
    URI redirect = google.callback(start.state(), "authorization-code", null);
    assertThat(redirect.toString()).doesNotContain("signed-google-token", "server-only-secret", start.exchangeSecret());
    return UriComponentsBuilder.fromUri(redirect).build().getQueryParams().getFirst("ticket");
  }

  private AuthService.DeviceContext device() { return new AuthService.DeviceContext("test", "ip-hash"); }
}