package com.loveos.api.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.loveos.api.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

class MailerTest {

  @Test
  void resetLinkTargetsTheConfiguredWebApplication() {
    Mailer mailer = mailer("https://app.example.com/");

    assertThat(mailer.passwordResetLink("reset-token"))
        .isEqualTo("https://app.example.com/reset-password?token=reset-token");
  }

  @Test
  void resetLinkPreservesTheExpoCustomSchemeSeparator() {
    Mailer mailer = mailer("talesoftwo://");

    assertThat(mailer.passwordResetLink("reset-token"))
        .isEqualTo("talesoftwo://reset-password?token=reset-token");
  }

  private static Mailer mailer(String publicAppUrl) {
    AppProperties properties = new AppProperties(
        new AppProperties.Jwt(
            "test-only-secret-with-at-least-32-characters",
            "loveos-test",
            Duration.ofMinutes(15),
            Duration.ofDays(30)),
        new AppProperties.Tokens(Duration.ofHours(24), Duration.ofMinutes(30), 32),
        new AppProperties.Mail("LoveOS <test@loveos.invalid>", "console"),
        "http://localhost:4000",
        publicAppUrl);

    @SuppressWarnings("unchecked")
    ObjectProvider<JavaMailSender> sender = mock(ObjectProvider.class);
    return new Mailer(properties, sender);
  }
}
