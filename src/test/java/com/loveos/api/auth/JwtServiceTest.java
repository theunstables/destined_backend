package com.loveos.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loveos.api.config.AppProperties;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = "test-only-secret-with-at-least-32-characters";

  @Test
  void issueAndVerifyRoundTripPreservesClaims() {
    JwtService service = service(Duration.ofMinutes(15), "loveos-test");
    UUID userId = UUID.randomUUID();

    String token = service.issueAccessToken(userId, "sam@example.com", true);
    JwtService.AccessClaims claims = service.verify(token);

    assertThat(claims.userId()).isEqualTo(userId);
    assertThat(claims.email()).isEqualTo("sam@example.com");
    assertThat(claims.emailVerified()).isTrue();
    assertThat(service.accessTokenSeconds()).isEqualTo(900);
  }

  @Test
  void tamperedTokenIsRejectedAsInvalid() {
    JwtService service = service(Duration.ofMinutes(15), "loveos-test");
    String token = service.issueAccessToken(UUID.randomUUID(), "sam@example.com", false);
    int signatureStart = token.lastIndexOf('.') + 1;
    char firstSignatureCharacter = token.charAt(signatureStart);
    String tampered = token.substring(0, signatureStart)
      + (firstSignatureCharacter == 'A' ? 'B' : 'A')
      + token.substring(signatureStart + 1);

    assertThatThrownBy(() -> service.verify(tampered))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.TOKEN_INVALID));
  }

  @Test
  void expiredTokenIsReportedSeparately() throws InterruptedException {
    JwtService service = service(Duration.ofMillis(1), "loveos-test");
    String token = service.issueAccessToken(UUID.randomUUID(), "sam@example.com", false);
    Thread.sleep(1100);

    assertThatThrownBy(() -> service.verify(token))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.TOKEN_EXPIRED));
  }

  @Test
  void wrongIssuerIsRejectedAsInvalid() {
    String token = service(Duration.ofMinutes(15), "issuer-a")
        .issueAccessToken(UUID.randomUUID(), "sam@example.com", true);

    assertThatThrownBy(() -> service(Duration.ofMinutes(15), "issuer-b").verify(token))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.TOKEN_INVALID));
  }

  private static JwtService service(Duration accessTtl, String issuer) {
    return new JwtService(new AppProperties(
        new AppProperties.Jwt(SECRET, issuer, accessTtl, Duration.ofDays(30)),
        new AppProperties.Tokens(Duration.ofHours(24), Duration.ofMinutes(30), 32),
        new AppProperties.Mail("LoveOS <test@loveos.invalid>", "console"),
        "http://localhost:4000"));
  }
}
