package com.loveos.api.dailyquestions;

import com.loveos.api.core.AppException;
import com.loveos.api.core.DateUtils;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.memories.MemoriesAccess;
import com.loveos.api.pairing.CoupleAccess;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyQuestionService {

  private final CoupleAccess couples;
  private final DailyQuestionCatalogRepository catalog;
  private final DailyQuestionDayRepository days;
  private final DailyQuestionAnswerRepository answers;
  private final MemoriesAccess memories;
  private final JdbcClient jdbc;

  public DailyQuestionService(
      CoupleAccess couples,
      DailyQuestionCatalogRepository catalog,
      DailyQuestionDayRepository days,
      DailyQuestionAnswerRepository answers,
      MemoriesAccess memories,
      JdbcClient jdbc) {
    this.couples = couples;
    this.catalog = catalog;
    this.days = days;
    this.answers = answers;
    this.memories = memories;
    this.jdbc = jdbc;
  }

  @Transactional
  public DailyQuestionDtos.DailyQuestion today(UUID userId) {
    CoupleAccess.Membership membership = connected(userId, false);
    DailyQuestionDay day = assignment(membership.coupleId(), DateUtils.utcToday());
    return response(userId, day);
  }

  @Transactional
  public DailyQuestionDtos.DailyQuestion answer(
      UUID userId, DailyQuestionDtos.SubmitAnswer input) {
    CoupleAccess.Membership membership = connected(userId, true);
    DailyQuestionDay current = assignment(
        membership.coupleId(), DateUtils.utcToday());
    DailyQuestionDay day = days.findByIdForUpdate(current.getId())
        .orElseThrow(() -> AppException.notFound("Daily question not found"));

    String text = input.answer().trim();
    DailyQuestionAnswer answer = answers.findByDayIdAndUserId(day.getId(), userId)
        .orElseGet(DailyQuestionAnswer::new);
    answer.setDayId(day.getId());
    answer.setUserId(userId);
    answer.setAnswer(text);
    answers.saveAndFlush(answer);

    List<DailyQuestionAnswer> all = answers.findByDayIdOrderByAnsweredAtAsc(day.getId());
    if (all.size() == 2) createOrUpdateMemory(userId, day, all);
    return response(userId, day);
  }

  private CoupleAccess.Membership connected(UUID userId, boolean update) {
    CoupleAccess.Membership membership = update
        ? couples.requireMembershipForUpdate(userId) : couples.requireMembership(userId);
    if (membership.state() != CoupleAccess.State.CONNECTED) {
      throw new AppException(ErrorCode.FORBIDDEN,
          "Connect with your partner to use the daily question");
    }
    return membership;
  }

  private DailyQuestionDay assignment(UUID coupleId, LocalDate date) {
    DailyQuestionDay existing = days.findByCoupleIdAndQuestionDate(coupleId, date).orElse(null);
    if (existing != null) return existing;

    List<DailyQuestionCatalogEntry> active = catalog.findByActiveTrueOrderByPositionAsc();
    if (active.isEmpty()) throw new IllegalStateException("Daily question catalogue is empty");
    DailyQuestionCatalogEntry selected = active.get(
        Math.floorMod(date.toEpochDay(), active.size()));
    jdbc.sql("""
        insert into daily_question_days
          (couple_id, question_key, question_date, prompt_snapshot)
        values (:coupleId, :questionKey, :questionDate, :prompt)
        on conflict (couple_id, question_date) do nothing
        """)
        .param("coupleId", coupleId)
        .param("questionKey", selected.getKey())
        .param("questionDate", date)
        .param("prompt", selected.getPrompt())
        .update();
    return days.findByCoupleIdAndQuestionDate(coupleId, date)
        .orElseThrow(() -> new IllegalStateException("Could not assign daily question"));
  }

  private void createOrUpdateMemory(
      UUID actingUserId, DailyQuestionDay day, List<DailyQuestionAnswer> all) {
    List<CoupleAccess.Member> members = couples.members(actingUserId);
    String note = all.stream().map(answer -> {
      CoupleAccess.Member member = members.stream()
          .filter(candidate -> candidate.userId().equals(answer.getUserId()))
          .findFirst().orElseThrow();
      return displayName(member) + ":\n" + answer.getAnswer();
    }).reduce((left, right) -> left + "\n\n" + right).orElse("");
    UUID memoryId = memories.upsertDailyQuestionMemory(
        actingUserId, day.getMemoryId(), DateUtils.format(day.getQuestionDate()),
        day.getPromptSnapshot(), note);
    if (day.getMemoryId() == null) {
      day.setMemoryId(memoryId);
      day.setCompletedAt(DateUtils.now());
    }
  }

  private DailyQuestionDtos.DailyQuestion response(UUID userId, DailyQuestionDay day) {
    List<DailyQuestionAnswer> all = answers.findByDayIdOrderByAnsweredAtAsc(day.getId());
    DailyQuestionAnswer mine = all.stream()
        .filter(answer -> answer.getUserId().equals(userId)).findFirst().orElse(null);
    DailyQuestionAnswer partner = all.stream()
        .filter(answer -> !answer.getUserId().equals(userId)).findFirst().orElse(null);
    boolean complete = mine != null && partner != null;
    String status = complete ? "complete" : mine == null ? "unanswered" : "waiting";
    return new DailyQuestionDtos.DailyQuestion(
        day.getId().toString(), DateUtils.format(day.getQuestionDate()),
        day.getPromptSnapshot(), status,
        partner != null, mine == null ? null : mine.getAnswer(),
        complete ? partner.getAnswer() : null,
        mine == null ? null : mine.getAnsweredAt().toString(),
        complete ? partner.getAnsweredAt().toString() : null,
        day.getMemoryId() == null ? null : day.getMemoryId().toString());
  }

  private static String displayName(CoupleAccess.Member member) {
    String nickname = text(member.nickname());
    if (nickname != null) return nickname;
    String displayName = text(member.displayName());
    return displayName == null ? member.role().name().toLowerCase(Locale.ROOT) : displayName;
  }

  private static String text(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
