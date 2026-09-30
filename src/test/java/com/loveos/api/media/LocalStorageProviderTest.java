package com.loveos.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageProviderTest {

  @TempDir java.nio.file.Path directory;

  @Test
  void storesAndLoadsBytesWithoutTemporaryFiles() throws Exception {
    LocalStorageProvider provider = new LocalStorageProvider(
        new MediaProperties("local", directory, 1024));
    byte[] content = {1, 2, 3};

    provider.store("51e41ecf-7af6-4262-a20b-5df025112ee7.jpg",
      new ByteArrayInputStream(content), content.length, "image/jpeg");

    var stored = provider.load("51e41ecf-7af6-4262-a20b-5df025112ee7.jpg").orElseThrow();
    assertThat(stored.content().readAllBytes()).containsExactly(content);
    assertThat(Files.list(directory).map(path -> path.getFileName().toString()))
        .noneMatch(name -> name.endsWith(".tmp"));
  }

  @Test
  void rejectsTraversalKeys() throws Exception {
    LocalStorageProvider provider = new LocalStorageProvider(
        new MediaProperties("local", directory, 1024));

    assertThatThrownBy(() -> provider.store(
      "../outside.jpg", new ByteArrayInputStream(new byte[0]), 0, "image/jpeg"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
