package com.loveos.api.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Production object storage using the AWS credential provider chain. */
@Component
@ConditionalOnProperty(name = "loveos.media.driver", havingValue = "s3")
public class S3StorageProvider implements StorageProvider {

  private final S3Client client;
  private final String bucket;
  private final String publicBaseUrl;

  public S3StorageProvider(S3MediaProperties properties) {
    if (blank(properties.bucket())) throw new IllegalStateException("MEDIA_S3_BUCKET is required");
    if (blank(properties.publicBaseUrl())) {
      throw new IllegalStateException("MEDIA_S3_PUBLIC_BASE_URL is required");
    }
    var builder = S3Client.builder().region(Region.of(properties.region()));
    if (!blank(properties.endpoint())) {
      builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
    }
    client = builder.build();
    bucket = properties.bucket();
    publicBaseUrl = properties.publicBaseUrl().replaceAll("/+$", "");
  }

  @Override
  public void store(String key, InputStream content, long bytes, String contentType) {
    client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
        .contentType(contentType).contentLength(bytes).build(),
        RequestBody.fromInputStream(content, bytes));
  }

  @Override
  public Optional<StoredObject> load(String key) throws IOException {
    try {
      var response = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
      return Optional.of(new StoredObject(response, response.response().contentLength()));
    } catch (NoSuchKeyException exception) {
      return Optional.empty();
    } catch (RuntimeException exception) {
      throw new IOException("S3 read failed", exception);
    }
  }

  @Override
  public String publicUrl(String key, String fallback) {
    return publicBaseUrl + "/" + key;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}