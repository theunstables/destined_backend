package com.loveos.api.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.loveos.api.config.AppProperties;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class MailerTest {

  private ObjectProvider<JavaMailSender> senderProvider;
  private RestClient.Builder clientBuilder;
  private MockRestServiceServer server;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    senderProvider = mock(ObjectProvider.class);
    clientBuilder = RestClient.builder();
    server = MockRestServiceServer.bindTo(clientBuilder).build();
  }

  @Test
  void resendVerificationUsesHttpsAndWebsiteLink(CapturedOutput output) {
    server.expect(requestTo("https://api.resend.com/emails"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-key"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.from").value("Tales of Two <no-reply@mail.example.test>"))
        .andExpect(jsonPath("$.to[0]").value("qa@example.test"))
        .andExpect(jsonPath("$.subject").value("Confirm your email"))
        .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString(
            "https://www.example.test/verify-email?token=verification%2B%2F%3D%20token")))
        .andRespond(withSuccess("{\"id\":\"message-123\"}", MediaType.APPLICATION_JSON));

    mailer("resend", "test-key", "https://www.example.test/")
        .sendVerification("qa@example.test", "verification+/= token");

    server.verify();
    verifyNoInteractions(senderProvider);
    assertThat(output.getAll()).contains("Email accepted by resend", "messageId=message-123")
      .doesNotContain("test-key", "verification%2B", "qa@example.test");
  }

  @Test
  void resendPasswordResetOpensTheWebsite() {
    server.expect(requestTo("https://api.resend.com/emails"))
        .andExpect(jsonPath("$.subject").value("Reset your password"))
        .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString(
            "https://www.example.test/reset-password?token=reset-token")))
        .andRespond(withSuccess("{\"id\":\"message-456\"}", MediaType.APPLICATION_JSON));

    mailer("resend", "test-key", "https://www.example.test")
        .sendPasswordReset("qa@example.test", "reset-token");

    server.verify();
    verifyNoInteractions(senderProvider);
  }

  @Test
  void smtpRemainsAvailableWithoutResendSettings() {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(senderProvider.getObject()).thenReturn(sender);

    mailer("smtp", "", "").sendVerification("qa@example.test", "token");

    ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(sender).send(message.capture());
    assertThat(message.getValue().getText())
        .contains("https://api.example.test/v1/auth/verify-email?token=token");
    server.verify();
  }

  @Test
  void resendRequiresAnApiKeyAndWebsiteUrl() {
    assertThatThrownBy(() -> mailer("resend", "", "https://www.example.test"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RESEND_API_KEY");
    assertThatThrownBy(() -> mailer("resend", "test-key", ""))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("FRONTEND_URL");
  }

      @ParameterizedTest
      @ValueSource(ints = {401, 403, 429, 500})
      void rejectedProviderRequestsAreLoggedWithoutLeakingResponseContent(int status, CapturedOutput output) {
      server.expect(requestTo("https://api.resend.com/emails"))
        .andRespond(withStatus(HttpStatusCode.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
          .body("{\"message\":\"provider-secret test-key verification-token qa@example.test\"}"));

      assertThatCode(() -> mailer("resend", "test-key", "https://www.example.test")
        .sendVerification("qa@example.test", "verification-token")).doesNotThrowAnyException();

      server.verify();
      verifyNoInteractions(senderProvider);
      assertThat(output.getAll()).contains("Email delivery via resend rejected", "status=" + status)
        .doesNotContain("provider-secret", "test-key", "verification-token", "qa@example.test", "Email accepted");
      }

      @Test
      void timeoutDoesNotLeakTheExceptionMessageOrFallBackToSmtp(CapturedOutput output) {
      server.expect(requestTo("https://api.resend.com/emails"))
        .andRespond(withException(new SocketTimeoutException("test-key verification-token qa@example.test")));

      assertThatCode(() -> mailer("resend", "test-key", "https://www.example.test")
        .sendVerification("qa@example.test", "verification-token")).doesNotThrowAnyException();

      server.verify();
      verifyNoInteractions(senderProvider);
      assertThat(output.getAll()).contains("Email delivery via resend failed")
        .doesNotContain("test-key", "verification-token", "qa@example.test", "Email accepted");
      }

      @ParameterizedTest
      @ValueSource(strings = {"{}", "{\"id\":\"\"}", "null", "not-json"})
      void missingOrInvalidProviderReceiptIsNotLoggedAsAccepted(String body, CapturedOutput output) {
      server.expect(requestTo("https://api.resend.com/emails"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

      assertThatCode(() -> mailer("resend", "test-key", "https://www.example.test")
        .sendVerification("qa@example.test", "verification-token")).doesNotThrowAnyException();

      server.verify();
      assertThat(output.getAll()).contains("Email delivery via resend failed").doesNotContain("Email accepted");
      }

      @ParameterizedTest
      @ValueSource(strings = {
        "http://www.example.test", "https://user:password@www.example.test",
        "https://www.example.test/?token=secret", "https://www.example.test/#fragment",
        "/relative", "not a URL"
      })
      void invalidWebsiteUrlsFailAtStartup(String frontendUrl) {
      assertThatThrownBy(() -> mailer("resend", "test-key", frontendUrl))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("FRONTEND_URL")
        .hasMessageNotContaining("password").hasMessageNotContaining("secret");
      }

      @Test
      void unknownDriverFailsInsteadOfSilentlyUsingSmtp() {
      assertThatThrownBy(() -> mailer("resned", "test-key", "https://www.example.test"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MAIL_DRIVER");
      verifyNoInteractions(senderProvider);
      }

      @Test
      void consoleModeDoesNotUseEitherNetworkTransport(CapturedOutput output) {
      mailer("console", "", "").sendVerification("qa@example.test", "development-token");

      server.verify();
      verifyNoInteractions(senderProvider);
      assertThat(output.getAll()).contains("EMAIL (console driver)");
      }

      @Test
      void smtpFailurePreservesAuthBehaviorWithoutLoggingItsContents(CapturedOutput output) {
      JavaMailSender sender = mock(JavaMailSender.class);
      when(senderProvider.getObject()).thenReturn(sender);
      doThrow(new MailSendException("smtp-password verification-token qa@example.test"))
        .when(sender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

      assertThatCode(() -> mailer("smtp", "", "")
        .sendVerification("qa@example.test", "verification-token")).doesNotThrowAnyException();

      assertThat(output.getAll()).contains("Email delivery via smtp failed")
        .doesNotContain("smtp-password", "verification-token", "qa@example.test", "Email accepted");
      }

      @Test
      void springWiresResendSettingsWithoutAnSmtpSender() {
      new ApplicationContextRunner()
        .withBean(AppProperties.class, () -> new AppProperties(null, null,
          new AppProperties.Mail("Tales of Two <no-reply@mail.example.test>", "resend"),
          "https://api.example.test"))
        .withUserConfiguration(Mailer.class)
        .withPropertyValues("loveos.mail.resend-api-key=test-key",
          "loveos.mail.frontend-url=https://www.example.test")
        .run(context -> {
          assertThat(context).hasNotFailed().hasSingleBean(Mailer.class);
          assertThat(context).doesNotHaveBean(JavaMailSender.class);
        });
      }

  private Mailer mailer(String driver, String key, String frontendUrl) {
    AppProperties properties = new AppProperties(null, null,
        new AppProperties.Mail("Tales of Two <no-reply@mail.example.test>", driver),
        "https://api.example.test/");
    return new Mailer(properties, senderProvider, key, frontendUrl, clientBuilder);
  }
}