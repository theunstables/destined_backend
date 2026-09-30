package com.loveos.api.calendar;

import com.loveos.api.calendar.domain.CalendarEvent;
import com.loveos.api.calendar.domain.CalendarEventKind;
import com.loveos.api.calendar.dto.CalendarRequests;
import com.loveos.api.calendar.dto.CalendarResponses;
import com.loveos.api.calendar.repo.CalendarEventRepository;
import com.loveos.api.core.AppException;
import com.loveos.api.core.DateUtils;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import com.loveos.api.story.domain.KeyDateKind;
import com.loveos.api.story.repo.KeyDateRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService implements CalendarAccess {

  private static final Map<KeyDateKind, String> KEY_LABELS = Map.of(
      KeyDateKind.ANNIVERSARY, "Our Anniversary",
      KeyDateKind.FIRST_DATE, "Our First Date",
      KeyDateKind.FIRST_MEETING, "The Day We Met",
      KeyDateKind.FOUNDER_BIRTHDAY, "Birthday",
      KeyDateKind.MEMBER_BIRTHDAY, "Birthday");

  private static final Map<KeyDateKind, String> KEY_NAMES = Map.of(
      KeyDateKind.ANNIVERSARY, "anniversary",
      KeyDateKind.FIRST_DATE, "firstDate",
      KeyDateKind.FIRST_MEETING, "firstMeeting",
      KeyDateKind.FOUNDER_BIRTHDAY, "founderBirthday",
      KeyDateKind.MEMBER_BIRTHDAY, "memberBirthday");

  private final CoupleAccess coupleAccess;
  private final CalendarEventRepository events;
  private final KeyDateRepository keyDates;

  public CalendarService(
      CoupleAccess coupleAccess,
      CalendarEventRepository events,
      KeyDateRepository keyDates) {
    this.coupleAccess = coupleAccess;
    this.events = events;
    this.keyDates = keyDates;
  }

  @Transactional(readOnly = true)
  public List<CalendarResponses.EventDto> list(
      UUID userId, String fromText, String toText, int limit) {
    UUID coupleId = coupleAccess.requireMembership(userId).coupleId();
    LocalDate from = nullableDate(fromText);
    LocalDate to = nullableDate(toText);
    if (from != null && to != null && to.isBefore(from)) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "to must not be before from");
    }
    var page = PageRequest.of(0, limit);
    List<CalendarEvent> rows;
    if (from != null && to != null) {
      rows = events.findByCoupleIdAndDateBetweenAndDeletedAtIsNullOrderByDateAscIdAsc(
          coupleId, from, to, page);
    } else if (from != null) {
      rows = events.findByCoupleIdAndDateGreaterThanEqualAndDeletedAtIsNullOrderByDateAscIdAsc(
          coupleId, from, page);
    } else if (to != null) {
      rows = events.findByCoupleIdAndDateLessThanEqualAndDeletedAtIsNullOrderByDateAscIdAsc(
          coupleId, to, page);
    } else {
      rows = events.findByCoupleIdAndDeletedAtIsNullOrderByDateAscIdAsc(coupleId, page);
    }
    return rows.stream().map(CalendarService::dto).toList();
  }

  @Transactional(readOnly = true)
  public CalendarResponses.EventDto get(UUID userId, UUID id) {
    UUID coupleId = coupleAccess.requireMembership(userId).coupleId();
    return dto(active(coupleId, id));
  }

  @Transactional
  public CalendarResponses.EventDto create(UUID userId, CalendarRequests.CreateEvent input) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    CalendarEvent event = new CalendarEvent();
    event.setCoupleId(coupleId);
    event.setCreatedById(userId);
    event.setTitle(required(input.title()));
    event.setDate(date(input.date()));
    event.setStartsAt(instant(input.startsAt()));
    event.setEndsAt(instant(input.endsAt()));
    event.setLocation(optional(input.location()));
    event.setNotes(optional(input.notes()));
    event.setKind(kind(input.kind()));
    event.setRepeatsAnnually(Boolean.TRUE.equals(input.repeatsAnnually()));
    event.setReminderMinutesBefore(input.reminderMinutesBefore());
    validateTimes(event);
    return dto(events.saveAndFlush(event));
  }

  @Transactional
  public CalendarResponses.EventDto update(
      UUID userId, UUID id, CalendarRequests.UpdateEvent input) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    CalendarEvent event = active(coupleId, id);
    if (input.title() != null) event.setTitle(required(input.title()));
    if (input.date() != null) event.setDate(date(input.date()));
    if (input.startsAt() != null) event.setStartsAt(instant(input.startsAt()));
    if (input.endsAt() != null) event.setEndsAt(instant(input.endsAt()));
    if (input.location() != null) event.setLocation(optional(input.location()));
    if (input.notes() != null) event.setNotes(optional(input.notes()));
    if (input.kind() != null) event.setKind(kind(input.kind()));
    if (input.repeatsAnnually() != null) event.setRepeatsAnnually(input.repeatsAnnually());
    if (input.reminderMinutesBefore() != null) {
      event.setReminderMinutesBefore(input.reminderMinutesBefore());
    }
    validateTimes(event);
    return dto(events.saveAndFlush(event));
  }

  @Transactional
  public void delete(UUID userId, UUID id) {
    UUID coupleId = coupleAccess.requireMembershipForUpdate(userId).coupleId();
    CalendarEvent event = active(coupleId, id);
    event.setDeletedAt(DateUtils.now());
    events.save(event);
  }

  @Transactional(readOnly = true)
  @Override
  public List<CalendarResponses.ComingUpDto> upcoming(
      UUID userId, int withinDays, int limit) {
    CoupleAccess.Membership membership = coupleAccess.requireMembership(userId);
    LocalDate today = DateUtils.utcToday();
    List<CalendarResponses.ComingUpDto> rows = new ArrayList<>();
    List<CoupleAccess.Member> members = coupleAccess.members(userId);

    keyDates.findByCoupleId(membership.coupleId()).forEach(keyDate -> {
      String label = birthdayLabel(keyDate.getKind(), userId, members);
      if (label == null) return;
      rows.add(new CalendarResponses.ComingUpDto(
          KEY_NAMES.get(keyDate.getKind()), label, shortDate(keyDate.getDate()),
          DateUtils.daysUntilNextAnnualOccurrence(keyDate.getDate(), today),
          DateUtils.format(keyDate.getDate()),
          isBirthday(keyDate.getKind()) ? "birthday" : "anniversary", "keyDate"));
    });

    events.findByCoupleIdAndDeletedAtIsNull(membership.coupleId()).forEach(event -> {
      long days = event.isRepeatsAnnually()
          ? DateUtils.daysUntilNextAnnualOccurrence(event.getDate(), today)
          : ChronoUnit.DAYS.between(today, event.getDate());
      if (days < 0) return;
      rows.add(new CalendarResponses.ComingUpDto(
          event.getId().toString(), event.getTitle(), shortDate(event.getDate()), days,
          DateUtils.format(event.getDate()), kind(event.getKind()), "event"));
    });

    return rows.stream()
        .filter(row -> row.days() <= withinDays)
        .sorted(Comparator.comparingLong(CalendarResponses.ComingUpDto::days)
            .thenComparing(CalendarResponses.ComingUpDto::key))
        .limit(limit)
        .toList();
  }

  static long daysUntilNextOccurrence(LocalDate original, LocalDate today) {
    return DateUtils.daysUntilNextAnnualOccurrence(original, today);
  }

  private CalendarEvent active(UUID coupleId, UUID id) {
    return events.findByIdAndCoupleIdAndDeletedAtIsNull(id, coupleId)
        .orElseThrow(() -> AppException.notFound("Event not found"));
  }

  private static void validateTimes(CalendarEvent event) {
    if (event.getStartsAt() != null && event.getEndsAt() != null
        && event.getEndsAt().isBefore(event.getStartsAt())) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "endsAt must not be before startsAt");
    }
  }

  private static String birthdayLabel(
      KeyDateKind kind, UUID viewer, List<CoupleAccess.Member> members) {
    if (!isBirthday(kind)) return KEY_LABELS.get(kind);
    CoupleAccess.Role ownerRole = kind == KeyDateKind.FOUNDER_BIRTHDAY
        ? CoupleAccess.Role.FOUNDER : CoupleAccess.Role.MEMBER;
    return members.stream().filter(member -> member.role() == ownerRole).findFirst()
        .map(member -> member.userId().equals(viewer)
            ? "Your Birthday"
            : optional(member.nickname()) != null
                ? optional(member.nickname()) + "'s Birthday"
                : optional(member.displayName()) != null
                    ? optional(member.displayName()) + "'s Birthday" : "Their Birthday")
        .orElse(null);
  }

  private static boolean isBirthday(KeyDateKind kind) {
    return kind == KeyDateKind.FOUNDER_BIRTHDAY || kind == KeyDateKind.MEMBER_BIRTHDAY;
  }

  private static CalendarResponses.EventDto dto(CalendarEvent event) {
    return new CalendarResponses.EventDto(
        event.getId().toString(), event.getTitle(), DateUtils.format(event.getDate()),
        text(event.getStartsAt()), text(event.getEndsAt()), event.getLocation(), event.getNotes(),
        kind(event.getKind()), event.isRepeatsAnnually(), event.getReminderMinutesBefore(),
        text(event.getCreatedAt()));
  }

  private static CalendarEventKind kind(String value) {
    if (value == null) return CalendarEventKind.CUSTOM;
    return switch (value) {
      case "anniversary" -> CalendarEventKind.ANNIVERSARY;
      case "birthday" -> CalendarEventKind.BIRTHDAY;
      case "dateNight" -> CalendarEventKind.DATE_NIGHT;
      case "trip" -> CalendarEventKind.TRIP;
      case "reminder" -> CalendarEventKind.REMINDER;
      case "custom" -> CalendarEventKind.CUSTOM;
      default -> throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid event kind");
    };
  }

  private static String kind(CalendarEventKind value) {
    return switch (value) {
      case ANNIVERSARY -> "anniversary";
      case BIRTHDAY -> "birthday";
      case DATE_NIGHT -> "dateNight";
      case TRIP -> "trip";
      case REMINDER -> "reminder";
      case CUSTOM -> "custom";
    };
  }

  private static LocalDate nullableDate(String value) {
    return value == null ? null : date(value);
  }

  private static LocalDate date(String value) {
    return DateUtils.parseCalendarDate(value);
  }

  private static Instant instant(String value) {
    return DateUtils.parseInstant(value);
  }

  private static String required(String value) {
    String result = optional(value);
    if (result == null) throw new AppException(ErrorCode.VALIDATION_ERROR, "Title is required");
    return result;
  }

  private static String optional(String value) {
    if (value == null) return null;
    String result = value.trim();
    return result.isEmpty() ? null : result;
  }

  private static String text(Instant value) {
    return DateUtils.format(value);
  }

  private static String shortDate(LocalDate date) {
    return date.getMonth().name().substring(0, 1)
        + date.getMonth().name().substring(1, 3).toLowerCase()
        + " " + date.getDayOfMonth();
  }
}