package com.loveos.api.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.loveos.api.auth.dto.AuthResponses;
import com.loveos.api.config.AppProperties;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
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
public class GoogleAuthService {
  private final GoogleAuthFlowRepository flows;
  private final GoogleTokenVerifier verifier;
  private final AuthService auth;
  private final TokenHasher tokens;
  private final String clientId;
  private final String clientSecret;
  private final String callbackUri;
  private final String webReturnUri;
  private final String nativeReturnUri;
  private final RestClient client;

  @Autowired
  public GoogleAuthService(GoogleAuthFlowRepository flows, GoogleTokenVerifier verifier, AuthService auth,
      TokenHasher tokens, AppProperties properties,
      @Value("${loveos.google.client-id:}") String clientId,
      @Value("${loveos.google.client-secret:}") String clientSecret,
      @Value("${loveos.mail.frontend-url:}") String frontendUrl,
      @Value("${loveos.google.native-redirect-uri:talesoftwo://google-callback}") String nativeReturnUri) {
    this(flows, verifier, auth, tokens, properties, clientId, clientSecret, frontendUrl, nativeReturnUri, clientBuilder());
  }

  GoogleAuthService(GoogleAuthFlowRepository flows, GoogleTokenVerifier verifier, AuthService auth,
      TokenHasher tokens, AppProperties properties, String clientId, String clientSecret,
      String frontendUrl, String nativeReturnUri, RestClient.Builder builder) {
    this.flows = flows;
    this.verifier = verifier;
    this.auth = auth;
    this.tokens = tokens;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.callbackUri = properties.publicApiUrl().replaceAll("/+$", "") + "/v1/auth/google/callback";
    this.webReturnUri = frontendUrl.replaceAll("/+$", "") + "/google-callback";
    this.nativeReturnUri = nativeReturnUri;
    this.client = builder.baseUrl("https://oauth2.googleapis.com").build();
    if (!clientId.isBlank() || !clientSecret.isBlank()) {
      if (clientId.isBlank() || clientSecret.isBlank()) throw new IllegalArgumentException("Google requires GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET");
      validateHttps(callbackUri);
      validateHttps(webReturnUri);
      URI nativeUri = URI.create(nativeReturnUri);
      if (!nativeUri.isAbsolute() || !"talesoftwo".equals(nativeUri.getScheme()) || nativeUri.getQuery() != null || nativeUri.getFragment() != null) {
        throw new IllegalArgumentException("GOOGLE_NATIVE_REDIRECT_URI must use the talesoftwo app scheme without query or fragment");
      }
    }
  }

  private static RestClient.Builder clientBuilder() {
    var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(8));
    return RestClient.builder().requestFactory(factory);
  }

  private static void validateHttps(String value) {
    URI uri = URI.create(value);
    if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
        || uri.getQuery() != null || uri.getFragment() != null) {
      throw new IllegalArgumentException("Google authentication requires HTTPS PUBLIC_API_URL and FRONTEND_URL");
    }
  }

  public boolean enabled() { return !clientId.isBlank() && !clientSecret.isBlank(); }

  @Transactional
  public Start start(String platform, UUID linkUserId) {
    if (!enabled()) throw new AppException(ErrorCode.INVALID_REQUEST, "Google sign-in is not configured");
    flows.deleteByExpiresAtBefore(Instant.now());
    String state = tokens.generate(32);
    String secret = tokens.generate(32);
    GoogleAuthFlow flow = new GoogleAuthFlow();
    flow.setStateHash(tokens.hash(state));
    flow.setNonce(tokens.generate(32));
    flow.setCodeVerifier(tokens.generate(32));
    flow.setExchangeSecretHash(tokens.hash(secret));
    flow.setReturnUri("native".equals(platform) ? nativeReturnUri : webReturnUri);
    flow.setLinkUserId(linkUserId);
    flow.setExpiresAt(Instant.now().plusSeconds(600));
    flows.save(flow);
    String authorizationUrl = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
        .queryParam("client_id", clientId).queryParam("redirect_uri", callbackUri)
        .queryParam("response_type", "code").queryParam("scope", "openid email profile")
        .queryParam("state", state).queryParam("nonce", flow.getNonce())
        .queryParam("code_challenge", challenge(flow.getCodeVerifier())).queryParam("code_challenge_method", "S256")
        .queryParam("prompt", "select_account").build().encode().toUriString();
    return new Start(authorizationUrl, state, secret, flow.getReturnUri());
  }

  @Transactional
  public URI callback(String state, String code, String error) {
    GoogleAuthFlow flow = flows.findByStateHash(tokens.hash(state))
        .orElseThrow(() -> AppException.tokenInvalid("Google login session is invalid"));
    if (flow.isCallbackUsed() || !flow.getExpiresAt().isAfter(Instant.now())) throw AppException.tokenExpired("Google login session expired");
    flow.setCallbackUsed(true);
    if (error != null || code == null || code.isBlank()) {
      flows.save(flow);
      return redirect(flow, state, null, "cancelled");
    }
    try {
      var form = new LinkedMultiValueMap<String, String>();
      form.add("grant_type", "authorization_code");
      form.add("code", code);
      form.add("client_id", clientId);
      form.add("client_secret", clientSecret);
      form.add("redirect_uri", callbackUri);
      form.add("code_verifier", flow.getCodeVerifier());
      TokenResponse response = client.post().uri("/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form).retrieve().body(TokenResponse.class);
      var identity = verifier.verify(response == null ? null : response.idToken(), flow.getNonce());
      flow.setSubject(identity.subject());
      flow.setEmail(identity.email());
      flow.setEmailAuthoritative(identity.emailAuthoritative());
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
    GoogleAuthFlow flow = flows.findByTicketHash(tokens.hash(ticket))
        .orElseThrow(() -> AppException.tokenInvalid("Google login session is invalid"));
    if (!flow.isCallbackUsed() || flow.getSubject() == null || !flow.getExpiresAt().isAfter(Instant.now())
        || !MessageDigest.isEqual(flow.getExchangeSecretHash().getBytes(StandardCharsets.UTF_8), tokens.hash(secret).getBytes(StandardCharsets.UTF_8))) {
      throw AppException.tokenInvalid("Google login session is invalid or expired");
    }
    var session = auth.googleSignIn(new GoogleTokenVerifier.Identity(flow.getSubject(), flow.getEmail(), flow.isEmailAuthoritative()), flow.getLinkUserId(), device);
    flows.delete(flow);
    flows.flush();
    return session;
  }

  private static URI redirect(GoogleAuthFlow flow, String state, String ticket, String error) {
    var uri = UriComponentsBuilder.fromUriString(flow.getReturnUri()).queryParam("state", state);
    if (ticket != null) uri.queryParam("ticket", ticket);
    if (error != null) uri.queryParam("error", error);
    return uri.build().encode().toUri();
  }

  private static String challenge(String verifier) {
    try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
    catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException("PKCE is unavailable"); }
  }

  public record Start(String authorizationUrl, String state, String exchangeSecret, String redirectUrl) {}
  private record TokenResponse(@JsonProperty("id_token") String idToken) {}
}