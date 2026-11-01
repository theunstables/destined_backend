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
import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class AppleAuthServiceTest {
  private final TokenHasher tokens = new TokenHasher();
  private Map<String, AppleAuthFlow> stored;
  private AppleAuthFlowRepository repository;
  private AppleTokenVerifier verifier;
  private AuthService auth;
  private AppleAuthService apple;
  private MockRestServiceServer server;
  private KeyPair pair;
  private String encodedKey;

  @BeforeEach
  void setUp() throws Exception {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    pair = generator.generateKeyPair();
    String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";
    encodedKey = Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    stored = new HashMap<>();
    repository = mock(AppleAuthFlowRepository.class);
    verifier = mock(AppleTokenVerifier.class);
    auth = mock(AuthService.class);
    when(repository.save(any())).thenAnswer(call -> {
      AppleAuthFlow flow = call.getArgument(0);
      stored.put(flow.getStateHash(), flow);
      return flow;
    });
    when(repository.findByStateHash(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
    when(repository.findByTicketHash(anyString())).thenAnswer(call -> stored.values().stream()
        .filter(flow -> call.getArgument(0).equals(flow.getTicketHash())).findFirst());
    doAnswer(call -> { stored.remove(((AppleAuthFlow) call.getArgument(0)).getStateHash()); return null; })
        .when(repository).delete(any(AppleAuthFlow.class));
    var builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    apple = configured(encodedKey, builder);
  }

  @Test
  void startUsesNonceFormPostAndConfiguredRedirectsWithoutSecrets() {
    var start = apple.start("web", null);
    var query = UriComponentsBuilder.fromUriString(start.authorizationUrl()).build().getQueryParams();
    AppleAuthFlow flow = stored.get(tokens.hash(start.state()));
    assertThat(query.getFirst("response_mode")).isEqualTo("form_post");
    assertThat(query.getFirst("response_type")).isEqualTo("code");
    assertThat(query.getFirst("scope")).isEqualTo("email");
    assertThat(query.getFirst("nonce")).isEqualTo(flow.getNonce());
    assertThat(start.redirectUrl()).isEqualTo("https://app.example.test/apple-callback");
    assertThat(start.authorizationUrl()).doesNotContain(start.exchangeSecret(), encodedKey, "client_secret");
    assertThat(flow.getExchangeSecretHash()).isEqualTo(tokens.hash(start.exchangeSecret()));
    assertThat(apple.start("native", null).redirectUrl()).isEqualTo("talesoftwo://apple-callback");
  }

  @Test
  void signsShortLivedClientSecretAndBindsSingleUseExchangeToInitiator() {
    var start = apple.start("web", null);
    String ticket = complete(start);
    assertThatThrownBy(() -> apple.exchange(ticket, "wrong-secret", device())).isInstanceOf(AppException.class);
    verifyNoInteractions(auth);
    apple.exchange(ticket, start.exchangeSecret(), device());
    verify(auth).appleSignIn(any(AppleTokenVerifier.Identity.class), isNull(), any());
    assertThatThrownBy(() -> apple.exchange(ticket, start.exchangeSecret(), device())).isInstanceOf(AppException.class);
    server.verify();
  }

  @Test
  void rejectsExpiredStateAndReplayedCallback() {
    var expired = apple.start("web", null);
    stored.get(tokens.hash(expired.state())).setExpiresAt(Instant.EPOCH);
    assertThatThrownBy(() -> apple.callback(expired.state(), "code", null)).isInstanceOf(AppException.class);
    var current = apple.start("web", null);
    complete(current);
    assertThatThrownBy(() -> apple.callback(current.state(), "code", null)).isInstanceOf(AppException.class);
    server.verify();
  }

  @Test
  void cancellationAndProviderErrorReturnNoTicket() {
    for (String error : java.util.List.of("user_cancelled_authorize", "server_error")) {
      var start = apple.start("web", null);
      URI redirect = apple.callback(start.state(), null, error);
      assertThat(redirect.toString()).startsWith(start.redirectUrl()).doesNotContain("ticket=", start.exchangeSecret());
      assertThat(redirect.toString()).contains("error=" + (error.equals("user_cancelled_authorize") ? "cancelled" : "provider_error"));
    }
    verifyNoInteractions(verifier, auth);
    server.verify();
  }

  @Test
  void expiredTicketCannotCreateASession() {
    var start = apple.start("web", null);
    String ticket = complete(start);
    stored.get(tokens.hash(start.state())).setExpiresAt(Instant.EPOCH);
    assertThatThrownBy(() -> apple.exchange(ticket, start.exchangeSecret(), device())).isInstanceOf(AppException.class);
    verifyNoInteractions(auth);
    server.verify();
  }

  @Test
  void malformedKeyFailsWithoutEchoingItsValue() {
    assertThatThrownBy(() -> configured("private-invalid-value", RestClient.builder()))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("APPLE_PRIVATE_KEY_BASE64")
        .hasMessageNotContaining("private-invalid-value").hasNoCause();
  }

  @Test
  void absentCredentialsDisableAppleWithoutStartingNetworkCalls() {
    var disabled = new AppleAuthService(repository, verifier, auth, tokens,
        new AppProperties(null, null, null, "http://localhost:4000"), "", "", "", "", "", "talesoftwo://apple-callback", RestClient.builder());
    assertThat(disabled.enabled()).isFalse();
    assertThatThrownBy(() -> disabled.start("web", null)).isInstanceOf(AppException.class);
    server.verify();
  }

  private String complete(AppleAuthService.Start start) {
    server.expect(requestTo("https://appleid.apple.com/auth/token")).andExpect(method(HttpMethod.POST))
        .andExpect(request -> {
          String body = ((MockClientHttpRequest) request).getBodyAsString();
          var form = UriComponentsBuilder.fromUriString("https://unused.test/?" + body).build().getQueryParams();
          String secret = URLDecoder.decode(form.getFirst("client_secret"), StandardCharsets.UTF_8);
          var jwt = Jwts.parser().verifyWith((ECPublicKey) pair.getPublic())
              .requireIssuer("TEAM123456").requireSubject("test-client").build().parseSignedClaims(secret);
          assertThat(jwt.getHeader().getAlgorithm()).isEqualTo("ES256");
          assertThat(jwt.getHeader().getKeyId()).isEqualTo("KEY1234567");
          assertThat(jwt.getPayload().getAudience()).containsExactly("https://appleid.apple.com");
          assertThat(Duration.between(jwt.getPayload().getIssuedAt().toInstant(), jwt.getPayload().getExpiration().toInstant()).toSeconds()).isEqualTo(300);
        }).andRespond(withSuccess("{\"id_token\":\"verified-token\"}", MediaType.APPLICATION_JSON));
    when(verifier.verify("verified-token", stored.get(tokens.hash(start.state())).getNonce()))
        .thenReturn(new AppleTokenVerifier.Identity("apple-subject", "qa@privaterelay.appleid.com"));
    URI redirect = apple.callback(start.state(), "authorization-code", null);
    assertThat(redirect.toString()).doesNotContain("verified-token", encodedKey, start.exchangeSecret());
    return UriComponentsBuilder.fromUri(redirect).build().getQueryParams().getFirst("ticket");
  }

  private AppleAuthService configured(String key, RestClient.Builder builder) {
    return new AppleAuthService(repository, verifier, auth, tokens,
        new AppProperties(null, null, null, "https://api.example.test"), "test-client", "TEAM123456", "KEY1234567",
        key, "https://app.example.test", "talesoftwo://apple-callback", builder);
  }

  private AuthService.DeviceContext device() { return new AuthService.DeviceContext("test", "ip-hash"); }
}