package com.loveos.api.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
public class MediaAsset {
  @Id
  @Column(name = "object_key", length = 64)
  private String objectKey;
  @Column(name = "couple_id", nullable = false)
  private UUID coupleId;
  @Column(name = "storage_driver", nullable = false, length = 16)
  private String storageDriver;
  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;
  @Column(nullable = false)
  private long bytes;
}