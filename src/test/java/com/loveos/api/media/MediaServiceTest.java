package com.loveos.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loveos.api.config.AppProperties;
import com.loveos.api.core.AppException;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaServiceTest {

  private CoupleAccess couples;
  private InMemoryStorage storage;
  private MediaService service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    couples = org.mockito.Mockito.mock(CoupleAccess.class);
    storage = new InMemoryStorage();
    AppProperties app = org.mockito.Mockito.mock(AppProperties.class);
    when(app.publicApiUrl()).thenReturn("https://api.example.test/");
    service = new MediaService(couples, storage,
        new MediaProperties("local", Path.of("unused"), 25 * 1024 * 1024), app);
    userId = UUID.randomUUID();
  }

  @Test
  void storesVerifiedBytesUnderRandomServerKey() {
    byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};

    var result = service.upload(userId,
        new MediaService.UploadInput(new ByteArrayInputStream(jpeg), "image/jpeg", jpeg.length));

    verify(couples).requireMembership(userId);
    assertThat(result.url()).matches("https://api\\.example\\.test/media/[0-9a-f-]{36}\\.jpg");
    assertThat(result.contentType()).isEqualTo("image/jpeg");
    assertThat(storage.bytes).containsExactly(jpeg);
  }

  @Test
  void rejectsClaimedImageWhoseBytesAreNotAnImage() {
    assertThatThrownBy(() -> service.upload(userId, new MediaService.UploadInput(
        new ByteArrayInputStream("not an image".getBytes()), "image/jpeg", 12)))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    assertThat(storage.bytes).isNull();
  }

  @Test
  void rejectsConfiguredOversizeBeforeStorage() {
    service = serviceWithMaximum(2);

    assertThatThrownBy(() -> service.upload(userId, new MediaService.UploadInput(
        new ByteArrayInputStream(new byte[3]), "image/jpeg", 3)))
        .isInstanceOfSatisfying(AppException.class,
            error -> assertThat(error.code()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
  }

  private MediaService serviceWithMaximum(long maximum) {
    AppProperties app = org.mockito.Mockito.mock(AppProperties.class);
    when(app.publicApiUrl()).thenReturn("https://api.example.test");
    return new MediaService(couples, storage,
        new MediaProperties("local", Path.of("unused"), maximum), app);
  }

  private static final class InMemoryStorage implements StorageProvider {
    private byte[] bytes;

    @Override
    public void store(String key, InputStream content, long length, String contentType)
      throws IOException {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      content.transferTo(output);
      bytes = output.toByteArray();
    }

    @Override
    public Optional<StoredObject> load(String key) {
      return Optional.empty();
    }
  }
}