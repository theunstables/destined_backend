package com.loveos.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loveos.api.config.AppProperties;
import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.GlobalExceptionHandler;
import com.loveos.api.pairing.CoupleAccess;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.springframework.web.client.RestClient;

class GitHubStorageProviderTest {
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }
  private static final String KEY = "3cd94be6-4665-4ebc-8eac-f84bf1dfa678.jpg";
  private static final String REPO = "https://api.github.com/repos/qa/private-media";
  private static final String CONTENT = REPO + "/contents/uploads/3c/" + KEY;
  private static final byte[] IMAGE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
  private MockRestServiceServer server;
  private GitHubStorageProvider storage;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    storage = new GitHubStorageProvider("qa", "private-media", "main", "test-pat",
      new MediaProperties("github", Path.of("unused"), 1024), builder);
  }

  @Test
  void uploadsOnlyToTheConfiguredPrivateRepository() throws Exception {
    privateRepository();
    server.expect(requestTo(CONTENT)).andExpect(method(HttpMethod.PUT))
        .andExpect(header("Authorization", "Bearer test-pat"))
        .andExpect(jsonPath("$.content").value(Base64.getEncoder().encodeToString(IMAGE)))
        .andExpect(jsonPath("$.branch").value("main"))
        .andRespond(withStatus(HttpStatus.CREATED));

    storage.store(KEY, new ByteArrayInputStream(IMAGE), IMAGE.length, "image/jpeg");
    server.verify();
  }

  @Test
  void backendFetchesRawBytesWithPatInsteadOfRedirectingTheBrowser() throws Exception {
    privateRepository();
    server.expect(requestTo(CONTENT + "?ref=main"))
        .andExpect(header("Authorization", "Bearer test-pat"))
        .andExpect(header("Accept", "application/vnd.github.raw+json"))
        .andRespond(withSuccess(IMAGE, MediaType.APPLICATION_OCTET_STREAM));
    var image = storage.load(KEY).orElseThrow();
    assertThat(image.content().readAllBytes()).containsExactly(IMAGE);
    assertThat(image.bytes()).isEqualTo(IMAGE.length);
    server.verify();
  }

  @Test
  void refusesAPublicRepositoryBeforeWritingAnyImage() {
    server.expect(requestTo(REPO)).andRespond(withSuccess("{\"private\":false}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> storage.store(KEY, new ByteArrayInputStream(IMAGE), IMAGE.length, "image/jpeg"))
        .isInstanceOf(IOException.class).hasMessageContaining("private repository");
    server.verify();
  }

  @Test
  void authorizedUserCanReadOwnedImageWithoutAGithubCredential() throws Exception {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
    byte[] png = output.toByteArray();
    privateRepository();
    server.expect(requestTo(org.hamcrest.Matchers.startsWith(REPO + "/contents/uploads/")))
        .andExpect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.CREATED));
    privateRepository();
    server.expect(requestTo(org.hamcrest.Matchers.startsWith(REPO + "/contents/uploads/")))
        .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer test-pat"))
        .andRespond(withSuccess(png, MediaType.APPLICATION_OCTET_STREAM));
    var app = mock(AppProperties.class);
    when(app.publicApiUrl()).thenReturn("https://api.example.test");
    UUID userId = UUID.randomUUID();
    UUID coupleId = UUID.randomUUID();
    var couples = mock(CoupleAccess.class);
    when(couples.requireMembership(userId)).thenReturn(new CoupleAccess.Membership(coupleId, CoupleAccess.Role.FOUNDER, true));
    var assets = mock(MediaAssetRepository.class);
    when(assets.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
      MediaAsset asset = call.getArgument(0);
      when(assets.findByObjectKeyAndCoupleIdAndStorageDriver(asset.getObjectKey(), coupleId, "github"))
        .thenReturn(java.util.Optional.of(asset));
      return asset;
    });
    var media = new MediaService(couples, storage,
      new MediaProperties("github", Path.of("unused"), 1024), app, assets);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
      new AuthenticatedUser(userId, "qa@example.test", true), null));
    var mvc = MockMvcBuilders.standaloneSetup(new MediaController(media))
      .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(new GlobalExceptionHandler()).build();

    var uploaded = media.upload(userId,
        new MediaService.UploadInput(new ByteArrayInputStream(png), "image/png", png.length));
    assertThat(uploaded.url()).startsWith("https://api.example.test/v1/media/").doesNotContain("signature", "test-pat", "github.com");
    var response = mvc.perform(get(URI.create(uploaded.url())))
        .andExpect(status().isOk()).andReturn().getResponse();

    assertThat(response.getContentType()).isEqualTo("image/png");
    assertThat(response.getContentAsByteArray()).containsExactly(png);
    assertThat(ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray())).getWidth()).isEqualTo(2);
    assertThat(response.getHeader("Location")).isNull();
    assertThat(response.getHeader("Authorization")).isNull();
    assertThat(response.getHeader("Cache-Control")).contains("no-store");
    assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    server.verify();
  }

  @Test
  void anonymousRequestIsRejectedEvenWithASignature() throws Exception {
    var app = mock(AppProperties.class);
    when(app.publicApiUrl()).thenReturn("https://api.example.test");
    var media = new MediaService(mock(CoupleAccess.class), storage,
      new MediaProperties("github", Path.of("unused"), 1024), app, mock(MediaAssetRepository.class));
    SecurityContextHolder.clearContext();
    var mvc = MockMvcBuilders.standaloneSetup(new MediaController(media))
      .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(new GlobalExceptionHandler()).build();
    mvc.perform(get("/media/" + KEY)).andExpect(status().isUnauthorized());
    mvc.perform(get("/media/" + KEY).param("signature", "bad")).andExpect(status().isUnauthorized());
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 429, 500})
  void providerFailureDoesNotExposeTokensOrResponseBody(int status) {
    server.expect(requestTo(REPO)).andRespond(withStatus(org.springframework.http.HttpStatusCode.valueOf(status))
        .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"test-pat private-provider-details\"}"));
    assertThatThrownBy(() -> storage.load(KEY)).isInstanceOf(IOException.class)
        .hasMessageContaining("HTTP " + status)
        .hasMessageNotContaining("test-pat").hasMessageNotContaining("private-provider-details")
        .hasNoCause();
    server.verify();
  }

  @Test
  void missingGithubFileReturnsNotFoundWithoutADownloadRedirect() throws Exception {
    privateRepository();
    server.expect(requestTo(CONTENT + "?ref=main")).andRespond(withStatus(HttpStatus.NOT_FOUND));
    assertThat(storage.load(KEY)).isEmpty();
    server.verify();
  }

  @Test
  void refusesReadsIfRepositoryIsMadePublic() {
    server.expect(requestTo(REPO)).andRespond(withSuccess("{\"private\":false}", MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> storage.load(KEY)).isInstanceOf(IOException.class)
        .hasMessageContaining("private repository");
    server.verify();
  }

  @Test
  void rejectsOversizedOrTruncatedUploadsBeforeCallingGithub() {
    assertThatThrownBy(() -> storage.store(KEY, new ByteArrayInputStream(IMAGE), 1025, "image/jpeg"))
        .isInstanceOf(IOException.class);
    assertThatThrownBy(() -> storage.store(KEY, new ByteArrayInputStream(IMAGE), 4, "image/jpeg"))
        .isInstanceOf(IOException.class);
    server.verify();
  }

  @Test
  void springLoadsOnlyWhenGithubDriverIsSelected() {
    new ApplicationContextRunner().withUserConfiguration(GitHubStorageProvider.class)
        .withBean(MediaProperties.class, () -> new MediaProperties("github", Path.of("unused"), 1024))
        .withPropertyValues("loveos.media.driver=github", "loveos.media.github.owner=qa",
            "loveos.media.github.repository=private-media", "loveos.media.github.token=test-pat")
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(GitHubStorageProvider.class));
  }

  private void privateRepository() {
    server.expect(requestTo(REPO)).andExpect(header("Authorization", "Bearer test-pat"))
        .andRespond(withSuccess("{\"private\":true}", MediaType.APPLICATION_JSON));
  }
}