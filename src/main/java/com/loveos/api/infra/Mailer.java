package com.loveos.api.infra;

import com.loveos.api.config.AppProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Outbound email.
 *
 * <p>THREE DRIVERS, CHOSEN BY CONFIGURATION. {@code console} prints the message —
 * including the link — to the log, which is what makes local development
 * possible without an SMTP server or a real inbox. {@code smtp} uses SMTP;
 * {@code resend} submits messages over HTTPS for hosts that block SMTP ports.
 * The switch is a setting rather than a profile check so that a staging
 * environment can run the real code path against a capture server.
 *
 * <p>SENDING NEVER FAILS A REQUEST. If the mail server is down, a sign-up should
 * still create the account — the user can ask for the verification email again,
 * whereas a failed registration loses their password entry and their patience.
 * So failures are logged and swallowed. The visible consequence is a missing
 * email, not a broken form.
 *
 * <p>{@link ObjectProvider} is used for the sender because {@code JavaMailSender}
 * only exists when mail properties are configured. Injecting it directly would
 * make the application fail to start in console mode.
 */
@Component
public class Mailer {

  private static final Logger log = LoggerFactory.getLogger(Mailer.class);

  private final AppProperties properties;
  private final ObjectProvider<JavaMailSender> senderProvider;
  private final String driver;
  private final String frontendUrl;
  private final RestClient resendClient;

  @Autowired
  public Mailer(
      AppProperties properties,
      ObjectProvider<JavaMailSender> senderProvider,
      @Value("${loveos.mail.resend-api-key:}") String resendApiKey,
      @Value("${loveos.mail.frontend-url:}") String frontendUrl) {
    this(properties, senderProvider, resendApiKey, frontendUrl, resendClientBuilder());
  }

  Mailer(
      AppProperties properties,
      ObjectProvider<JavaMailSender> senderProvider,
      String resendApiKey,
      String frontendUrl,
      RestClient.Builder clientBuilder) {
    this.properties = properties;
    this.senderProvider = senderProvider;
    this.driver = properties.mail().driver().toLowerCase(Locale.ROOT);
    this.frontendUrl = frontendUrl.strip().replaceAll("/+$", "");
    if (!Set.of("console", "smtp", "resend").contains(driver)) {
      throw new IllegalArgumentException("MAIL_DRIVER must be console, smtp, or resend");
    }
    if ("resend".equals(driver) && (resendApiKey.isBlank() || this.frontendUrl.isBlank())) {
      throw new IllegalArgumentException("MAIL_DRIVER=resend requires RESEND_API_KEY and FRONTEND_URL");
    }
    if (!this.frontendUrl.isBlank()) {
      URI uri;
      try {
        uri = URI.create(this.frontendUrl);
      } catch (IllegalArgumentException invalid) {
        throw new IllegalArgumentException("FRONTEND_URL must be an absolute HTTPS URL");
      }
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
          || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
        throw new IllegalArgumentException("FRONTEND_URL must be an absolute HTTPS URL without credentials, query, or fragment");
      }
    }
    this.resendClient = "resend".equals(driver)
        ? clientBuilder.baseUrl("https://api.resend.com")
            .defaultHeaders(headers -> headers.setBearerAuth(resendApiKey.strip()))
            .build()
        : null;
  }

  private static RestClient.Builder resendClientBuilder() {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(8));
    return RestClient.builder().requestFactory(factory);
  }

  private String actionLink(String path, String token) {
    String base = frontendUrl.isBlank() ? properties.publicApiUrl().replaceAll("/+$", "") : frontendUrl;
    return UriComponentsBuilder.fromUriString(base).path(path)
        .queryParam("token", "{token}").encode().buildAndExpand(token).toUriString();
  }

  public void sendVerification(String to, String token) {
    String link = actionLink(frontendUrl.isBlank() ? "/v1/auth/verify-email" : "/verify-email", token);
    send(to, "Confirm your email", """
        Welcome to LoveOS.

        Confirm your email address to finish setting up your account:

        %s

        If you did not create an account, you can ignore this message.
        """.formatted(link));
  }

  public void sendPasswordReset(String to, String token) {
    String link = actionLink("/reset-password", token);
    send(to, "Reset your password", """
        Someone asked to reset the password for this account.

        %s

        This link expires in 30 minutes and can be used once. If it wasn't you,
        nothing has changed and you can ignore this message.
        """.formatted(link));
  }

  private void send(String to, String subject, String body) {
    if ("console".equals(driver)) {
      log.info("""

          ──────────── EMAIL (console driver) ────────────
          To      : {}
          Subject : {}
          {}
          ────────────────────────────────────────────────
          """, to, subject, body);
      return;
    }

    try {
      if ("resend".equals(driver)) {
        ResendResponse response = resendClient.post().uri("/emails")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("from", properties.mail().from(), "to", List.of(to),
                "subject", subject, "text", body))
            .retrieve().body(ResendResponse.class);
        if (response == null || response.id() == null || response.id().isBlank()) {
          throw new IllegalStateException("Resend did not return a message ID");
        }
        log.info("Email accepted by resend: subject='{}', messageId={}", subject, response.id());
        return;
      }
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(properties.mail().from());
      message.setTo(to);
      message.setSubject(subject);
      message.setText(body);
      senderProvider.getObject().send(message);
      log.info("Email accepted by smtp: subject='{}'", subject);
    } catch (RestClientResponseException failure) {
      log.error("Email delivery via {} rejected: subject='{}', status={}",
          driver, subject, failure.getStatusCode().value());
    } catch (Exception failure) {
      // Deliberately swallowed — see the class note.
      log.error("Email delivery via {} failed: subject='{}', failureType={}",
          driver, subject, failure.getClass().getSimpleName());
    }
  }

  private record ResendResponse(String id) {}
}
