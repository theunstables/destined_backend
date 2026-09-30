package com.loveos.api.dailyquestions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "daily_question_days")
@Getter
@Setter
@NoArgsConstructor
class DailyQuestionDay {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "couple_id", nullable = false, columnDefinition = "uuid")
  private UUID coupleId;

  @Column(name = "question_key", nullable = false, length = 60)
  private String questionKey;

  @Column(name = "question_date", nullable = false)
  private LocalDate questionDate;

  @Column(name = "prompt_snapshot", nullable = false, length = 500)
  private String promptSnapshot;

  @Column(name = "memory_id", columnDefinition = "uuid")
  private UUID memoryId;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
