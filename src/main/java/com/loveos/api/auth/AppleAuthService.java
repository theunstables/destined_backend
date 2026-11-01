package com.loveos.api.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.loveos.api.auth.dto.AuthResponses;
import com.loveos.api.config.AppProperties;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AppleAuthService {
  private final AppleAuthFlowRepository flows;
  private final AppleTokenVerifier verifier;
  private final AuthService auth;
  private final TokenHasher tokens;
  private final String clientId;
  private final String teamId;
  private final String keyId;
  private final ECPrivateKey privateKey;
  private final String callbackUri;
  private final String webReturnUri;
  private final String nativeReturnUri;
  private final RestClient client;

  @Autowired
  public AppleAuthService(AppleAuthFlowRepository flows, AppleTokenVerifier verifier, AuthService auth,
      TokenHasher tokens, AppProperties properties,
      @Value("${loveos.apple.client-id:}") String clientId,
      @Value("${loveos.apple.team-id:}") String teamId,
      @Value("${loveos.apple.key-id:}") String keyId,
      @Value("${loveos.apple.private-key-base64:}") String encodedKey,
      @Value("${loveos.mail.frontend-url:}") String frontendUrl,
      @Value("${loveos.apple.native-redirect-uri:talesoftwo://apple-callback}") String nativeReturnUri) {
    this(flows, verifier, auth, tokens, properties, clientId, teamId, keyId, encodedKey, frontendUrl, nativeReturnUri, clientBuilder());
  }

  AppleAuthService(AppleAuthFlowRepository flows, AppleTokenVerifier verifier, AuthService auth,
      TokenHasher tokens, AppProperties properties, String clientId, String teamId, String keyId,
      String encodedKey, String frontendUrl, String nativeReturnUri, RestClient.Builder builder) {
    this.flows = flows;
    this.verifier = verifier;
    this.auth = auth;
    this.tokens = tokens;
    this.clientId = clientId;
    this.teamId = teamId;
    this.keyId = keyId;
    this.callbackUri = properties.publicApiUrl().replaceAll("/+$", "") + "/v1/auth/apple/callback";
    this.webReturnUri = frontendUrl.replaceAll("/+$", "") + "/apple-callback";
    this.nativeReturnUri = nativeReturnUri;
    this.client = builder.baseUrl("https://appleid.apple.com").build();
    if (clientId.isBlank() && teamId.isBlank() && keyId.isBlank() && encodedKey.isBlank()) {
      privateKey = null;
      return;
    }
    if (clientId.isBlank() || !teamId.matches("[A-Z0-9]{10}") || !keyId.matches("[A-Z0-9]{10}") || encodedKey.isBlank()) {
      throw new IllegalArgumentException("Apple requires APPLE_CLIENT_ID, APPLE_TEAM_ID, APPLE_KEY_ID and APPLE_PRIVATE_KEY_BASE64");
    }
    validateHttps(callbackUri);
    validateHttps(webReturnUri);
    URI nativeUri = URI.create(nativeReturnUri);
    if (!nativeUri.isAbsolute() || !"talesoftwo".equals(nativeUri.getScheme()) || nativeUri.getQuery() != null
        || nativeUri.getFragment() != null || nativeUri.getUserInfo() != null) {
      throw new IllegalArgumentException("APPLE_NATIVE_REDIRECT_URI must use the talesoftwo app scheme without credentials, query, or fragment");
    }
    try {
      String pem = new String(Base64.getDecoder().decode(encodedKey.strip()), StandardCharsets.UTF_8);
      String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
      privateKey = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
      if (privateKey.getParams().getOrder().bitLength() != 256) throw new IllegalArgumentException();
    } catch (Exception invalid) {
      throw new IllegalArgumentException("APPLE_PRIVATE_KEY_BASE64 must contain a base64-encoded Apple .p8 PEM signing key");
    }
  }

  private static RestClient.Builder clientBuilder() {
    var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(8));
    return RestClient.builder().requestFactory(factory);
  }

  private static void validateHttps(String value) {
    URI uri = URI.create(value);
    if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !uri.getHost().contains(".")
        || uri.getHost().matches("[0-9.]+") || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
      throw new IllegalArgumentException("Apple authentication requires HTTPS PUBLIC_API_URL and FRONTEND_URL with real domains");
    }
  }

  public boolean enabled() { return privateKey != null; }

  @Transactional
  public Start start(String platform, UUID linkUserId) {
    if (!enabled()) throw new AppException(ErrorCode.INVALID_REQUEST, "Apple sign-in is not configured");
    flows.deleteByExpiresAtBefore(Instant.now());
    String state = tokens.generate(32);
    String secret = tokens.generate(32);
    AppleAuthFlow flow = new AppleAuthFlow();
    flow.setStateHash(tokens.hash(state));
    flow.setNonce(tokens.generate(32));
    flow.setExchangeSecretHash(tokens.hash(secret));
    flow.setReturnUri("native".equals(platform) ? nativeReturnUri : webReturnUri);
    flow.setLinkUserId(linkUserId);
    flow.setExpiresAt(Instant.now().plusSeconds(600));
    flows.save(flow);
    String url = UriComponentsBuilder.fromUriString("https://appleid.apple.com/auth/authorize")
        .queryParam("client_id", clientId).queryParam("redirect_uri", callbackUri)
        .queryParam("response_type", "code").queryParam("response_mode", "form_post").queryParam("scope", "email")
        .queryParam("state", state).queryParam("nonce", flow.getNonce()).build().encode().toUriString();
    return new Start(url, state, secret, flow.getReturnUri());
  }

  @Transactional
  public URI callback(String state, String code, String error) {
    if (state == null || state.isBlank() || state.length() > 256) throw AppException.tokenInvalid("Apple login session is invalid");
    AppleAuthFlow flow = flows.findByStateHash(tokens.hash(state))
        .orElseThrow(() -> AppException.tokenInvalid("Apple login session is invalid"));
    if (flow.isCallbackUsed() || !flow.getExpiresAt().isAfter(Instant.now())) throw AppException.tokenExpired("Apple login session expired");
    flow.setCallbackUsed(true);
    if (error != null || code == null || code.isBlank()) {
      flows.save(flow);
      return redirect(flow, state, null, "user_cancelled_authorize".equals(error) ? "cancelled" : "provider_error");
    }
    try {
      if (!enabled() || code.length() > 4096) throw AppException.tokenInvalid("Apple code is invalid");
      var form = new LinkedMultiValueMap<String, String>();
      form.add("grant_type", "authorization_code");
      form.add("code", code);
      form.add("client_id", clientId);
      form.add("client_secret", clientSecret());
      form.add("redirect_uri", callbackUri);
      TokenResponse response = client.post().uri("/auth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form).retrieve().body(TokenResponse.class);
      var identity = verifier.verify(response == null ? null : response.idToken(), flow.getNonce());
      flow.setSubject(identity.subject());
      flow.setEmail(identity.email());
    } catch (Exception failure) {
      flows.save(flow);
      return redirect(flow, state, null, "provider_error");
    }
    String ticket = tokens.generate(32);
    flow.setTicketHash(tokens.hash(ticket));
    flow.setExpiresAt(Instant.now().plusSeconds(120));
    flows.save(flow);
    return redirect(flow, state, ticket, null);
  }

  @Transactional
  public AuthResponses.SessionDto exchange(String ticket, String secret, AuthService.DeviceContext device) {
    AppleAuthFlow flow = flows.findByTicketHash(tokens.hash(ticket))
        .orElseThrow(() -> AppException.tokenInvalid("Apple login session is invalid"));
    if (!flow.isCallbackUsed() || flow.getSubject() == null || !flow.getExpiresAt().isAfter(Instant.now())
        || !MessageDigest.isEqual(flow.getExchangeSecretHash().getBytes(StandardCharsets.UTF_8), tokens.hash(secret).getBytes(StandardCharsets.UTF_8))) {
      throw AppException.tokenInvalid("Apple login session is invalid or expired");
    }
    var session = auth.appleSignIn(new AppleTokenVerifier.Identity(flow.getSubject(), flow.getEmail()), flow.getLinkUserId(), device);
    flows.delete(flow);
    flows.flush();
    return session;
  }

  private String clientSecret() {
    Instant now = Instant.now();
    return Jwts.builder().header().keyId(keyId).and().issuer(teamId).subject(clientId)
        .audience().add("https://appleid.apple.com").and().issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(300))).signWith(privateKey, Jwts.SIG.ES256).compact();
  }

  private static URI redirect(AppleAuthFlow flow, String state, String ticket, String error) {
    var uri = UriComponentsBuilder.fromUriString(flow.getReturnUri()).queryParam("state", state);
    if (ticket != null) uri.queryParam("ticket", ticket);
    if (error != null) uri.queryParam("error", error);
    return uri.build().encode().toUri();
  }

  public record Start(String authorizationUrl, String state, String exchangeSecret, String redirectUrl) {}
  private record TokenResponse(@JsonProperty("id_token") String idToken) {}
}