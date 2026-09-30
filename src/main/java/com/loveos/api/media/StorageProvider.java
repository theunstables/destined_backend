package com.loveos.api.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** Binary-storage boundary. Production object storage can replace the local adapter. */
public interface StorageProvider {

  void store(String key, InputStream content, long bytes, String contentType) throws IOException;

  Optional<StoredObject> load(String key) throws IOException;

  default String publicUrl(String key, String fallback) {
    return fallback;
  }

  record StoredObject(InputStream content, long bytes) {}
}