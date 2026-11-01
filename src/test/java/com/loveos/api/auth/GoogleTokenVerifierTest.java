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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GoogleTokenVerifierTest {
  private KeyPair pair;
  private GoogleTokenVerifier verifier;
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
    String keys = json.writeValueAsString(Map.of("keys", java.util.List.of(Map.of(
        "kid", "test-key", "kty", "RSA", "alg", "RS256",
        "n", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()),
        "e", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray())))));
    server.expect(requestTo("https://www.googleapis.com/oauth2/v3/certs"))
        .andRespond(withSuccess(keys, MediaType.APPLICATION_JSON));
    verifier = new GoogleTokenVerifier("test-client", json, builder);
  }

  @Test
  void verifiesSignedGoogleIdentityAndNonce() {
    var identity = verifier.verify(token("test-client", "https://accounts.google.com", "nonce", true, 300), "nonce");
    assertThat(identity.subject()).isEqualTo("google-subject");
    assertThat(identity.email()).isEqualTo("qa@example.test");
    assertThat(identity.emailAuthoritative()).isFalse();
    server.verify();
  }

  @Test
  void rejectsWrongAudienceIssuerNonceUnverifiedEmailAndExpiredTokens() {
    for (String invalid : java.util.List.of(
        token("other-client", "https://accounts.google.com", "nonce", true, 300),
        token("test-client", "https://evil.example", "nonce", true, 300),
        token("test-client", "https://accounts.google.com", "wrong", true, 300),
        token("test-client", "https://accounts.google.com", "nonce", false, 300),
        token("test-client", "https://accounts.google.com", "nonce", true, -60))) {
      assertThatThrownBy(() -> verifier.verify(invalid, "nonce")).isInstanceOf(AppException.class);
    }
    server.verify();
  }

  @Test
  void rejectsTamperedPayload() {
    String valid = token("test-client", "https://accounts.google.com", "nonce", true, 300);
    String[] parts = valid.split("\\.");
    String changed = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"attacker\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];
    assertThatThrownBy(() -> verifier.verify(changed, "nonce")).isInstanceOf(AppException.class);
    server.verify();
  }

  private String token(String audience, String issuer, String nonce, boolean verified, int expiry) {
    return Jwts.builder().header().keyId("test-key").and().subject("google-subject").issuer(issuer)
        .audience().add(audience).and().issuedAt(Date.from(Instant.now().minusSeconds(120)))
        .expiration(Date.from(Instant.now().plusSeconds(expiry)))
        .claim("email", "qa@example.test").claim("email_verified", verified).claim("nonce", nonce)
        .signWith(pair.getPrivate(), Jwts.SIG.RS256).compact();
  }
}