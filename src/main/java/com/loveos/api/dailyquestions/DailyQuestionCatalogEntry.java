package com.loveos.api.dailyquestions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_question_catalog")
@Getter
@NoArgsConstructor
class DailyQuestionCatalogEntry {

  @Id
  @Column(name = "question_key", length = 60)
  private String key;

  @Column(nullable = false, unique = true)
  private int position;

  @Column(nullable = false, length = 500)
  private String prompt;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
