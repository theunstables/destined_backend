package com.loveos.api.infra;

import com.loveos.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Outbound email.
 *
 * <p>TWO DRIVERS, CHOSEN BY CONFIGURATION. {@code console} prints the message —
 * including the link — to the log, which is what makes local development
 * possible without an SMTP server or a real inbox. {@code smtp} sends properly.
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

  public Mailer(AppProperties properties, ObjectProvider<JavaMailSender> senderProvider) {
    this.properties = properties;
    this.senderProvider = senderProvider;
  }

  public void sendVerification(String to, String token) {
    String link = properties.publicApiUrl() + "/v1/auth/verify-email?token=" + token;
    send(to, "Confirm your email", """
        Welcome to LoveOS.

        Confirm your email address to finish setting up your account:

        %s

        If you did not create an account, you can ignore this message.
        """.formatted(link));
  }

  public void sendPasswordReset(String to, String token) {
    String link = properties.publicApiUrl() + "/reset-password?token=" + token;
    send(to, "Reset your password", """
        Someone asked to reset the password for this account.

        %s

        This link expires in 30 minutes and can be used once. If it wasn't you,
        nothing has changed and you can ignore this message.
        """.formatted(link));
  }

  private void send(String to, String subject, String body) {
    if ("console".equalsIgnoreCase(properties.mail().driver())) {
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
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(properties.mail().from());
      message.setTo(to);
      message.setSubject(subject);
      message.setText(body);
      senderProvider.getObject().send(message);
    } catch (Exception failure) {
      // Deliberately swallowed — see the class note.
      log.error("failed to send '{}' to {}", subject, to, failure);
    }
  }
}
