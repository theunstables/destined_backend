package com.loveos.api.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "loveos.media.driver", havingValue = "local", matchIfMissing = true)
public class LocalStorageProvider implements StorageProvider {

  private final Path root;

  public LocalStorageProvider(MediaProperties properties) throws IOException {
    root = properties.localDir().toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  @Override
  public void store(String key, InputStream content, long bytes, String contentType) throws IOException {
    Path destination = resolve(key);
    Path temporary = Files.createTempFile(root, ".upload-", ".tmp");
    try {
      Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
      try {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, destination);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  @Override
  public Optional<StoredObject> load(String key) throws IOException {
    Path path = resolve(key);
    if (!Files.isRegularFile(path)) return Optional.empty();
    return Optional.of(new StoredObject(Files.newInputStream(path), Files.size(path)));
  }

  private Path resolve(String key) {
    Path resolved = root.resolve(key).normalize();
    if (!resolved.getParent().equals(root)) throw new IllegalArgumentException("Invalid storage key");
    return resolved;
  }
}