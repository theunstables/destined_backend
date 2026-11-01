package com.loveos.api.media;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "loveos.media.driver", havingValue = "github")
public class GitHubStorageProvider implements StorageProvider {
  private final RestClient client;
  private final String repositoryPath;
  private final String branch;
  private final int maxBytes;

  @Autowired
  public GitHubStorageProvider(
      @Value("${loveos.media.github.owner:}") String owner,
      @Value("${loveos.media.github.repository:}") String repository,
      @Value("${loveos.media.github.branch:main}") String branch,
      @Value("${loveos.media.github.token:}") String token,
      MediaProperties properties) {
    this(owner, repository, branch, token, properties, clientBuilder());
  }

  GitHubStorageProvider(String owner, String repository, String branch, String token,
      MediaProperties properties, RestClient.Builder builder) {
    if (!owner.matches("[A-Za-z0-9][A-Za-z0-9-]{0,99}")
        || !repository.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")) {
      throw new IllegalArgumentException("MEDIA_GITHUB_OWNER and MEDIA_GITHUB_REPOSITORY are required repository names");
    }
    if (branch.isBlank() || token.isBlank()) {
      throw new IllegalArgumentException("GitHub media requires a branch and MEDIA_GITHUB_TOKEN");
    }
    if (properties.maxBytes() <= 0 || properties.maxBytes() > 25 * 1024 * 1024) {
      throw new IllegalArgumentException("GitHub media supports MEDIA_MAX_BYTES up to 25 MiB");
    }
    this.repositoryPath = "/repos/" + owner + "/" + repository;
    this.branch = branch;
    this.maxBytes = (int) properties.maxBytes();
    this.client = builder.baseUrl("https://api.github.com")
        .defaultHeaders(headers -> {
          headers.setBearerAuth(token.strip());
          headers.set("X-GitHub-Api-Version", "2022-11-28");
          headers.set("User-Agent", "TalesOfTwo-Media");
        }).build();
  }

  private static RestClient.Builder clientBuilder() {
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(40));
    return RestClient.builder().requestFactory(factory);
  }

  private void requirePrivateRepository() throws IOException {
    RepositoryInfo repository = client.get().uri(repositoryPath)
        .accept(MediaType.valueOf("application/vnd.github+json"))
        .retrieve().body(RepositoryInfo.class);
    if (repository == null || !repository.privateRepository()) {
      throw new IOException("GitHub image storage requires a private repository");
    }
  }

  private String contentPath(String key) {
    if (!key.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp|heic|mp4|mov|m4a|mp3|webm|ogg)")) {
      throw new IllegalArgumentException("Invalid media key");
    }
    return repositoryPath + "/contents/uploads/" + key.substring(0, 2) + "/" + key;
  }

  @Override
  public synchronized void store(String key, InputStream content, long bytes, String contentType) throws IOException {
    String path = contentPath(key);
    if (bytes <= 0 || bytes > maxBytes) throw new IOException("Media exceeds the configured size limit");
    byte[] data = content.readNBytes(maxBytes + 1);
    if (data.length != bytes) throw new IOException("Media length does not match the upload");
    try {
      requirePrivateRepository();
      client.put().uri(path).contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("message", "Store uploaded media", "branch", branch,
              "content", Base64.getEncoder().encodeToString(data)))
          .retrieve().toBodilessEntity();
    } catch (RestClientResponseException failure) {
      throw new IOException("GitHub media upload failed (HTTP " + failure.getStatusCode().value() + ")");
    } catch (RuntimeException failure) {
      throw new IOException("GitHub media upload failed");
    }
  }

  @Override
  public Optional<StoredObject> load(String key) throws IOException {
    String path = contentPath(key);
    try {
      requirePrivateRepository();
      byte[] data = client.get()
          .uri(builder -> builder.path(path).queryParam("ref", branch).build())
          .accept(MediaType.valueOf("application/vnd.github.raw+json"))
          .exchange((request, response) -> {
            if (response.getStatusCode().value() == 404) return null;
            if (!response.getStatusCode().is2xxSuccessful()) {
              throw new IOException("GitHub media read failed (HTTP " + response.getStatusCode().value() + ")");
            }
            byte[] bytes = response.getBody().readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new IOException("Stored media exceeds the configured size limit");
            return bytes;
          });
      return data == null ? Optional.empty()
          : Optional.of(new StoredObject(new ByteArrayInputStream(data), data.length));
    } catch (RestClientResponseException failure) {
      throw new IOException("GitHub media read failed (HTTP " + failure.getStatusCode().value() + ")");
    } catch (RuntimeException failure) {
      throw new IOException("GitHub media read failed");
    }
  }

  private record RepositoryInfo(@JsonProperty("private") boolean privateRepository) {}
}