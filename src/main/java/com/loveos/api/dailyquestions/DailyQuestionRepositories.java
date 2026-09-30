package com.loveos.api.dailyquestions;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DailyQuestionCatalogRepository extends JpaRepository<DailyQuestionCatalogEntry, String> {
  List<DailyQuestionCatalogEntry> findByActiveTrueOrderByPositionAsc();
}

interface DailyQuestionDayRepository extends JpaRepository<DailyQuestionDay, UUID> {
  Optional<DailyQuestionDay> findByCoupleIdAndQuestionDate(UUID coupleId, LocalDate questionDate);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select day from DailyQuestionDay day where day.id = :id")
  Optional<DailyQuestionDay> findByIdForUpdate(@Param("id") UUID id);
}

interface DailyQuestionAnswerRepository extends JpaRepository<DailyQuestionAnswer, UUID> {
  List<DailyQuestionAnswer> findByDayIdOrderByAnsweredAtAsc(UUID dayId);
  Optional<DailyQuestionAnswer> findByDayIdAndUserId(UUID dayId, UUID userId);
}
