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
public class AppleTokenVerifier {
  private final String clientId;
  private final ObjectMapper json;
  private final RestClient client;
  private Map<String, RSAPublicKey> keys = Map.of();
  private Instant keyExpiry = Instant.EPOCH;

  @Autowired
  public AppleTokenVerifier(@Value("${loveos.apple.client-id:}") String clientId, ObjectMapper json) {
    this(clientId, json, clientBuilder());
  }

  AppleTokenVerifier(String clientId, ObjectMapper json, RestClient.Builder builder) {
    this.clientId = clientId;
    this.json = json;
    this.client = builder.baseUrl("https://appleid.apple.com").build();
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
      Claims claims = Jwts.parser().verifyWith(signingKey(header.path("kid").asText()))
          .requireIssuer("https://appleid.apple.com").require("nonce", nonce).build()
          .parseSignedClaims(token).getPayload();
      if (!Set.of(clientId).equals(claims.getAudience()) || claims.getExpiration() == null
          || claims.getIssuedAt() == null || claims.getIssuedAt().toInstant().isAfter(Instant.now().plusSeconds(60))
          || !truth(claims.get("email_verified"))) throw invalid();
      String subject = claims.getSubject();
      String email = claims.get("email", String.class);
      if (subject == null || subject.isBlank() || subject.length() > 255
          || email == null || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid();
      return new Identity(subject, email);
    } catch (Exception failure) {
      throw invalid();
    }
  }

  private synchronized RSAPublicKey signingKey(String kid) throws Exception {
    if (kid.isBlank() || kid.length() > 256) throw invalid();
    if (Instant.now().isAfter(keyExpiry) || !keys.containsKey(kid)) {
      var response = client.get().uri("/auth/keys").retrieve().toEntity(JsonNode.class);
      JsonNode jwks = response.getBody();
      Map<String, RSAPublicKey> refreshed = new HashMap<>();
      if (jwks == null || !jwks.path("keys").isArray()) throw invalid();
      for (JsonNode candidate : jwks.path("keys")) {
        if (!"RSA".equals(candidate.path("kty").asText()) || !"RS256".equals(candidate.path("alg").asText())
            || !"sig".equals(candidate.path("use").asText())) continue;
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(candidate.path("n").asText()));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(candidate.path("e").asText()));
        refreshed.put(candidate.path("kid").asText(), (RSAPublicKey) KeyFactory.getInstance("RSA")
            .generatePublic(new RSAPublicKeySpec(modulus, exponent)));
      }
      keys = Map.copyOf(refreshed);
      long lifetime = 3600;
      String cacheControl = response.getHeaders().getCacheControl();
      if (cacheControl != null) {
        var maxAge = java.util.regex.Pattern.compile("(?:^|,)\\s*max-age=(\\d+)").matcher(cacheControl);
        if (maxAge.find()) lifetime = Math.min(3600, Long.parseLong(maxAge.group(1)));
      }
      keyExpiry = Instant.now().plusSeconds(lifetime);
    }
    RSAPublicKey key = keys.get(kid);
    if (key == null) throw invalid();
    return key;
  }

  private static boolean truth(Object value) { return Boolean.TRUE.equals(value) || "true".equals(value); }

  private static AppException invalid() {
    return new AppException(ErrorCode.INVALID_PROVIDER_TOKEN, "Apple identity could not be verified");
  }

  public record Identity(String subject, String email) {}
}