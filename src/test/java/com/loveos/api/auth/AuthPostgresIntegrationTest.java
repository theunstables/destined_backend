package com.loveos.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.loveos.api.auth.dto.AuthRequests;
import com.loveos.api.auth.repo.UserRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** Exercises Auth, JPA, Flyway and the genuine PostgreSQL engine together. */
@SpringBootTest
@TestPropertySource(properties = {
    "loveos.jwt.secret=test-only-secret-with-at-least-32-characters",
    "loveos.mail.driver=console",
    "spring.flyway.baseline-on-migrate=false"
})
@Transactional
class AuthPostgresIntegrationTest {

  @Autowired private AuthService authService;
  @Autowired private UserRepository users;

  @Test
  void signupAndLoginRoundTripAgainstMigratedPostgres() {
    String email = "integration-" + UUID.randomUUID() + "@loveos.test";
    var device = new AuthService.DeviceContext("integration-test", "test-ip-hash");

    var signup = authService.signUp(new AuthRequests.SignUp(email, "Password1"), device);
    var login = authService.login(new AuthRequests.Login(email, "Password1"), device);

    assertThat(signup.user().userId()).isEqualTo(login.user().userId());
    assertThat(users.findByEmail(email)).isPresent();
    assertThat(login.refreshToken()).isNotBlank();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class PostgresTestConfiguration {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres testPostgres() throws IOException {
      return EmbeddedPostgres.builder().setPort(0).start();
    }

    @Bean
    @Primary
    DataSource dataSource(EmbeddedPostgres postgres) {
      return postgres.getPostgresDatabase();
    }
  }
}
