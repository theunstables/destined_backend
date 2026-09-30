package com.loveos.api.story.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "story_moments")
@Getter
@Setter
@NoArgsConstructor
public class StoryMoment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "story_id", nullable = false)
  private UUID storyId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MomentKind kind;

  private LocalDate date;

  @Column(length = 120)
  private String location;

  @Column(length = 2000)
  private String note;

  @Column(name = "photo_url", length = 2048)
  private String photoUrl;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}