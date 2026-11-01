package com.loveos.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.loveos.api.core.AppException;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AppleTokenVerifierTest {
  private KeyPair pair;
  private AppleTokenVerifier verifier;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    pair = generator.generateKeyPair();
    RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
    var builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    var json = new ObjectMapper();
    String keys = json.writeValueAsString(Map.of("keys", List.of(Map.of(
        "kid", "test-key", "kty", "RSA", "alg", "RS256", "use", "sig",
        "n", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()),
        "e", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray())))));
    server.expect(requestTo("https://appleid.apple.com/auth/keys"))
        .andRespond(withSuccess(keys, MediaType.APPLICATION_JSON));
    verifier = new AppleTokenVerifier("test-client", json, builder);
  }

  @Test
  void verifiesRelayEmailAndBooleanOrStringVerificationClaims() {
    for (Object verified : List.of(true, "true")) {
      var identity = verifier.verify(token("test-client", "https://appleid.apple.com", "nonce", verified, 300), "nonce");
      assertThat(identity.subject()).isEqualTo("apple-subject");
      assertThat(identity.email()).isEqualTo("qa@privaterelay.appleid.com");
    }
    server.verify();
  }

  @Test
  void rejectsWrongAudienceIssuerNonceUnverifiedEmailAndExpiredTokens() {
    for (String invalid : List.of(
        token("other-client", "https://appleid.apple.com", "nonce", true, 300),
        token("test-client", "https://evil.example", "nonce", true, 300),
        token("test-client", "https://appleid.apple.com", "wrong", true, 300),
        token("test-client", "https://appleid.apple.com", "nonce", false, 300),
        token("test-client", "https://appleid.apple.com", "nonce", "false", 300),
        token("test-client", "https://appleid.apple.com", "nonce", true, -60))) {
      assertThatThrownBy(() -> verifier.verify(invalid, "nonce")).isInstanceOf(AppException.class);
    }
    server.verify();
  }

  @Test
  void rejectsTamperedPayload() {
    String[] parts = token("test-client", "https://appleid.apple.com", "nonce", true, 300).split("\\.");
    String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
        .encodeToString("{\"sub\":\"attacker\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];
    assertThatThrownBy(() -> verifier.verify(tampered, "nonce")).isInstanceOf(AppException.class);
    server.verify();
  }

  private String token(String audience, String issuer, String nonce, Object verified, int expiry) {
    return Jwts.builder().header().keyId("test-key").and().subject("apple-subject").issuer(issuer)
        .audience().add(audience).and().issuedAt(Date.from(Instant.now().minusSeconds(120)))
        .expiration(Date.from(Instant.now().plusSeconds(expiry)))
        .claim("email", "qa@privaterelay.appleid.com").claim("email_verified", verified).claim("nonce", nonce)
        .signWith(pair.getPrivate(), Jwts.SIG.RS256).compact();
  }
}