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
  @Autowired private org.springframework.web.context.WebApplicationContext context;

  @Test
  void anonymousMediaRequestsAreRejectedByTheRealSecurityChain() throws Exception {
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
        .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    for (String path : java.util.List.of("/media/", "/v1/media/")) {
      mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
          .get(path + "3cd94be6-4665-4ebc-8eac-f84bf1dfa678.jpg").param("signature", "old-signature"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }
  }

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

    @Test
    void appleFormPostOriginIsAllowedOnlyOnTheCallback() throws Exception {
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
      .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/auth/apple/callback")
      .header("Origin", "https://appleid.apple.com")
      .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
      .param("state", "unknown-state").param("code", "code"))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code").value("TOKEN_INVALID"))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", "https://appleid.apple.com"));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/auth/apple/callback")
      .header("Origin", "https://evil.example")
      .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
      .param("state", "unknown-state"))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/auth/apple/start")
      .header("Origin", "https://appleid.apple.com")
      .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
      .content("{\"platform\":\"web\",\"link\":false}"))
      .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
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
