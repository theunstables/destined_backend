package com.loveos.api.story;

import com.loveos.api.core.AppException;
import com.loveos.api.core.DateUtils;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import com.loveos.api.story.domain.DatePrecision;
import com.loveos.api.story.domain.KeyDate;
import com.loveos.api.story.domain.KeyDateKind;
import com.loveos.api.story.domain.MomentKind;
import com.loveos.api.story.domain.Story;
import com.loveos.api.story.domain.StoryMoment;
import com.loveos.api.story.dto.StoryRequests;
import com.loveos.api.story.dto.StoryResponses;
import com.loveos.api.story.repo.KeyDateRepository;
import com.loveos.api.story.repo.StoryMomentRepository;
import com.loveos.api.story.repo.StoryRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists the complete onboarding story within the caller's couple scope. */
@Service
public class StoryService implements StoryAccess {

  private final CoupleAccess coupleAccess;
  private final StoryRepository stories;
  private final StoryMomentRepository moments;
  private final KeyDateRepository keyDates;

  public StoryService(
      CoupleAccess coupleAccess,
      StoryRepository stories,
      StoryMomentRepository moments,
      KeyDateRepository keyDates) {
    this.coupleAccess = coupleAccess;
    this.stories = stories;
    this.moments = moments;
    this.keyDates = keyDates;
  }

  @Transactional(readOnly = true)
  public StoryResponses.StoryDto getStory(UUID userId) {
    CoupleAccess.Membership membership = coupleAccess.requireMembership(userId);
    return readStory(membership);
  }

  @Override
  @Transactional(readOnly = true)
  public LocalDate metDate(UUID userId) {
    UUID coupleId = coupleAccess.requireMembership(userId).coupleId();
    return stories.findByCoupleId(coupleId).map(Story::getMetDate).orElse(null);
  }

  /** PUT semantics: omitted moments and dates are removed, so retries converge exactly. */
  @Transactional
  public StoryResponses.StoryDto saveStory(UUID userId, StoryRequests.SaveStory input) {
    CoupleAccess.Membership membership = coupleAccess.requireMembershipForUpdate(userId);
    Story story = stories.findByCoupleId(membership.coupleId()).orElseGet(Story::new);
    story.setCoupleId(membership.coupleId());
    story.setMetDate(input.met() == null ? null : date(input.met().value()));
    story.setMetPrecision(input.met() == null ? null : precision(input.met().precision()));
    stories.saveAndFlush(story);

    moments.deleteByStoryId(story.getId());
    moments.flush();
    List<StoryMoment> replacementMoments = new ArrayList<>();
    addMoment(replacementMoments, story.getId(), MomentKind.FIRST_DATE, input.firstDate());
    addMoment(replacementMoments, story.getId(), MomentKind.BECAME_US, input.becameUs());
    addMoment(replacementMoments, story.getId(), MomentKind.FIRST_MEMORY, input.firstMemory());
    moments.saveAll(replacementMoments);

    keyDates.deleteByCoupleId(membership.coupleId());
  keyDates.flush();
    Map<KeyDateKind, LocalDate> replacementDates = keyDateValues(membership, input);
    replacementDates.forEach((kind, value) -> {
      KeyDate keyDate = new KeyDate();
      keyDate.setCoupleId(membership.coupleId());
      keyDate.setKind(kind);
      keyDate.setDate(value);
      keyDates.save(keyDate);
    });

    return readStory(membership);
  }

  private StoryResponses.StoryDto readStory(CoupleAccess.Membership membership) {
    Story story = stories.findByCoupleId(membership.coupleId()).orElse(null);
    Map<KeyDateKind, LocalDate> dates = new EnumMap<>(KeyDateKind.class);
    keyDates.findByCoupleId(membership.coupleId())
        .forEach(keyDate -> dates.put(keyDate.getKind(), keyDate.getDate()));
    BirthdayKinds birthdays = birthdays(membership.role());
    StoryResponses.KeyDates keyDateDto = new StoryResponses.KeyDates(
        text(dates.get(KeyDateKind.ANNIVERSARY)),
        text(dates.get(birthdays.mine())),
        text(dates.get(birthdays.theirs())),
        text(dates.get(KeyDateKind.FIRST_DATE)),
        text(dates.get(KeyDateKind.FIRST_MEETING)));

    if (story == null) {
      return new StoryResponses.StoryDto(null, null, null, null, keyDateDto);
    }

    Map<MomentKind, StoryMoment> byKind = new EnumMap<>(MomentKind.class);
    moments.findByStoryId(story.getId()).forEach(moment -> byKind.put(moment.getKind(), moment));
    StoryResponses.StoryDate met = story.getMetDate() == null ? null
        : new StoryResponses.StoryDate(
            story.getMetDate().toString(), precision(story.getMetPrecision()));
    return new StoryResponses.StoryDto(
        met,
        moment(byKind.get(MomentKind.FIRST_DATE)),
        moment(byKind.get(MomentKind.BECAME_US)),
        moment(byKind.get(MomentKind.FIRST_MEMORY)),
        keyDateDto);
  }

  private static Map<KeyDateKind, LocalDate> keyDateValues(
      CoupleAccess.Membership membership, StoryRequests.SaveStory input) {
    Map<KeyDateKind, LocalDate> values = new EnumMap<>(KeyDateKind.class);
    BirthdayKinds birthdays = birthdays(membership.role());
    StoryRequests.KeyDates supplied = input.keyDates();
    if (supplied != null) {
      put(values, KeyDateKind.ANNIVERSARY, supplied.anniversary());
      put(values, birthdays.mine(), supplied.yourBirthday());
      put(values, birthdays.theirs(), supplied.partnerBirthday());
      put(values, KeyDateKind.FIRST_DATE, supplied.firstDate());
      put(values, KeyDateKind.FIRST_MEETING, supplied.firstMeeting());
    }
    if (!values.containsKey(KeyDateKind.FIRST_MEETING) && input.met() != null) {
      values.put(KeyDateKind.FIRST_MEETING, date(input.met().value()));
    }
    if (!values.containsKey(KeyDateKind.FIRST_DATE)
        && input.firstDate() != null && input.firstDate().date() != null) {
      values.put(KeyDateKind.FIRST_DATE, date(input.firstDate().date()));
    }
    return values;
  }

  private static void put(Map<KeyDateKind, LocalDate> values, KeyDateKind kind, String value) {
    if (value != null) values.put(kind, date(value));
  }

  private static void addMoment(
      List<StoryMoment> target, UUID storyId, MomentKind kind, StoryRequests.Moment input) {
    if (input == null) return;
    StoryMoment moment = new StoryMoment();
    moment.setStoryId(storyId);
    moment.setKind(kind);
    moment.setDate(input.date() == null ? null : date(input.date()));
    moment.setLocation(optional(input.location()));
    moment.setNote(optional(input.note()));
    moment.setPhotoUrl(input.photoUri());
    target.add(moment);
  }

  private static StoryResponses.Moment moment(StoryMoment moment) {
    if (moment == null) return null;
    return new StoryResponses.Moment(
        text(moment.getDate()), moment.getLocation(), moment.getNote(), moment.getPhotoUrl());
  }

  private static LocalDate date(String value) {
    return DateUtils.parseCalendarDate(value);
  }

  private static DatePrecision precision(String value) {
    return switch (value) {
      case "exact" -> DatePrecision.EXACT;
      case "monthYear" -> DatePrecision.MONTH_YEAR;
      case "yearOnly" -> DatePrecision.YEAR_ONLY;
      default -> throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid date precision");
    };
  }

  private static String precision(DatePrecision precision) {
    return switch (precision) {
      case EXACT -> "exact";
      case MONTH_YEAR -> "monthYear";
      case YEAR_ONLY -> "yearOnly";
    };
  }

  private static BirthdayKinds birthdays(CoupleAccess.Role role) {
    return role == CoupleAccess.Role.FOUNDER
        ? new BirthdayKinds(KeyDateKind.FOUNDER_BIRTHDAY, KeyDateKind.MEMBER_BIRTHDAY)
        : new BirthdayKinds(KeyDateKind.MEMBER_BIRTHDAY, KeyDateKind.FOUNDER_BIRTHDAY);
  }

  private static String optional(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String text(LocalDate value) {
    return value == null ? null : value.toString();
  }

  private record BirthdayKinds(KeyDateKind mine, KeyDateKind theirs) {}
}