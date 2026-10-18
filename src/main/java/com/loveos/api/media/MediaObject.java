package com.loveos.api.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_objects")
public class MediaObject {
  @Id
  @Column(name = "object_key", nullable = false, length = 128)
  private String objectKey;

  @Column(name = "couple_id", nullable = false)
  private UUID coupleId;

  @Column(name = "uploaded_by", nullable = false)
  private UUID uploadedBy;

  @Column(name = "public_url", nullable = false, length = 2048)
  private String publicUrl;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(nullable = false)
  private long bytes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public String getObjectKey() { return objectKey; }
  public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
  public UUID getCoupleId() { return coupleId; }
  public void setCoupleId(UUID coupleId) { this.coupleId = coupleId; }
  public UUID getUploadedBy() { return uploadedBy; }
  public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
  public String getPublicUrl() { return publicUrl; }
  public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
  public String getContentType() { return contentType; }
  public void setContentType(String contentType) { this.contentType = contentType; }
  public long getBytes() { return bytes; }
  public void setBytes(long bytes) { this.bytes = bytes; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
