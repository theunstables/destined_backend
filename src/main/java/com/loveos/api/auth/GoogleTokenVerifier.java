package com.loveos.api.auth;

import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GoogleTokenVerifier {
  private final String clientId;
  private final ObjectMapper json;
  private final RestClient client;
  private Map<String, RSAPublicKey> keys = Map.of();
  private Instant keyExpiry = Instant.EPOCH;

  @Autowired
  public GoogleTokenVerifier(@Value("${loveos.google.client-id:}") String clientId, ObjectMapper json) {
    this(clientId, json, clientBuilder());
  }

  GoogleTokenVerifier(String clientId, ObjectMapper json, RestClient.Builder builder) {
    this.clientId = clientId;
    this.json = json;
    this.client = builder.baseUrl("https://www.googleapis.com").build();
  }

  private static RestClient.Builder clientBuilder() {
    var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    return RestClient.builder().requestFactory(factory);
  }

  public Identity verify(String token, String nonce) {
    try {
      if (clientId.isBlank() || token == null || token.length() > 16_000 || nonce == null || nonce.isBlank()) throw invalid();
      String[] parts = token.split("\\.", -1);
      if (parts.length != 3) throw invalid();
      JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
      if (!"RS256".equals(header.path("alg").asText())) throw invalid();
      RSAPublicKey key = signingKey(header.path("kid").asText());
      Claims claims = Jwts.parser().verifyWith(key).require("nonce", nonce).build()
          .parseSignedClaims(token).getPayload();
      if (!Set.of("https://accounts.google.com", "accounts.google.com").contains(claims.getIssuer())
          || claims.getAudience() == null || !claims.getAudience().contains(clientId)
          || (claims.getAudience().size() > 1 && !clientId.equals(claims.get("azp", String.class)))
          || (claims.containsKey("azp") && !clientId.equals(claims.get("azp", String.class)))
          || claims.getExpiration() == null || claims.getIssuedAt() == null
          || claims.getIssuedAt().toInstant().isAfter(Instant.now().plusSeconds(60))
          || !Boolean.TRUE.equals(claims.get("email_verified", Boolean.class))) throw invalid();
      String subject = claims.getSubject();
      String email = claims.get("email", String.class);
      if (subject == null || subject.isBlank() || subject.length() > 255
          || email == null || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid();
        boolean authoritative = email.toLowerCase(java.util.Locale.ROOT).endsWith("@gmail.com")
          || (claims.get("hd", String.class) != null && !claims.get("hd", String.class).isBlank());
        return new Identity(subject, email, authoritative);
    } catch (Exception failure) {
      throw invalid();
    }
  }

  private synchronized RSAPublicKey signingKey(String kid) throws Exception {
    if (kid.isBlank() || kid.length() > 256) throw invalid();
    if (Instant.now().isAfter(keyExpiry) || !keys.containsKey(kid)) {
      JsonNode jwks = client.get().uri("/oauth2/v3/certs").retrieve().body(JsonNode.class);
      Map<String, RSAPublicKey> refreshed = new HashMap<>();
      if (jwks == null || !jwks.path("keys").isArray()) throw invalid();
      for (JsonNode candidate : jwks.path("keys")) {
        if (!"RSA".equals(candidate.path("kty").asText()) || !"RS256".equals(candidate.path("alg").asText())) continue;
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(candidate.path("n").asText()));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(candidate.path("e").asText()));
        refreshed.put(candidate.path("kid").asText(), (RSAPublicKey) KeyFactory.getInstance("RSA")
            .generatePublic(new RSAPublicKeySpec(modulus, exponent)));
      }
      keys = Map.copyOf(refreshed);
      keyExpiry = Instant.now().plusSeconds(3600);
    }
    RSAPublicKey key = keys.get(kid);
    if (key == null) throw invalid();
    return key;
  }

  private static AppException invalid() {
    return new AppException(ErrorCode.INVALID_PROVIDER_TOKEN, "Google identity could not be verified");
  }

  public record Identity(String subject, String email, boolean emailAuthoritative) {
    public Identity(String subject, String email) {
      this(subject, email, email.toLowerCase(java.util.Locale.ROOT).endsWith("@gmail.com"));
    }
  }
}