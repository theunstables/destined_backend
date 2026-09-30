package com.loveos.api.calendar.repo;

import com.loveos.api.calendar.domain.CalendarEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

  Optional<CalendarEvent> findByIdAndCoupleIdAndDeletedAtIsNull(UUID id, UUID coupleId);

  List<CalendarEvent> findByCoupleIdAndDeletedAtIsNullOrderByDateAscIdAsc(
      UUID coupleId, Pageable pageable);

  List<CalendarEvent> findByCoupleIdAndDateBetweenAndDeletedAtIsNullOrderByDateAscIdAsc(
      UUID coupleId, LocalDate from, LocalDate to, Pageable pageable);

  List<CalendarEvent> findByCoupleIdAndDateGreaterThanEqualAndDeletedAtIsNullOrderByDateAscIdAsc(
      UUID coupleId, LocalDate from, Pageable pageable);

  List<CalendarEvent> findByCoupleIdAndDateLessThanEqualAndDeletedAtIsNullOrderByDateAscIdAsc(
      UUID coupleId, LocalDate to, Pageable pageable);

  List<CalendarEvent> findByCoupleIdAndDeletedAtIsNull(UUID coupleId);
}