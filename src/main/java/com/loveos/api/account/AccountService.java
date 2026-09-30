package com.loveos.api.account;

import com.loveos.api.auth.repo.UserRepository;
import com.loveos.api.core.AppException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

  private static final int DELETION_GRACE_DAYS = 30;

  private final AccountDeletionRequestRepository deletions;
  private final UserRepository users;
  private final JdbcClient jdbc;

  AccountService(
      AccountDeletionRequestRepository deletions, UserRepository users, JdbcClient jdbc) {
    this.deletions = deletions;
    this.users = users;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public DeletionStatus deletionStatus(UUID userId) {
    requireUser(userId);
    return deletions.findById(userId).map(this::status)
        .orElse(new DeletionStatus(false, null, null, null, null));
  }

  @Transactional
  public DeletionStatus requestDeletion(UUID userId) {
    requireUser(userId);
    Instant now = Instant.now();
    AccountDeletionRequest request = deletions.findById(userId)
        .orElseGet(AccountDeletionRequest::new);
    request.setUserId(userId);
    request.setRequestedAt(now);
    request.setExecuteAfter(now.plus(DELETION_GRACE_DAYS, ChronoUnit.DAYS));
    request.setCancelledAt(null);
    request.setExecutedAt(null);
    deletions.save(request);
    return status(request);
  }

  @Transactional
  public DeletionStatus cancelDeletion(UUID userId) {
    requireUser(userId);
    return deletions.findById(userId).map(request -> {
      if (request.getCancelledAt() == null && request.getExecutedAt() == null) {
        request.setCancelledAt(Instant.now());
      }
      return status(request);
    }).orElse(new DeletionStatus(false, null, null, null, null));
  }

  @Transactional(readOnly = true)
  public AccountExport export(UUID userId) {
    requireUser(userId);
    Map<String, Object> sections = new LinkedHashMap<>();
    sections.put("profile", rows("""
        select id, email, email_verified, display_name, nickname, pronouns, birthday,
               photo_url, created_at, updated_at
          from users where id = :userId
        """, "userId", userId));

    UUID coupleId = jdbc.sql("select couple_id from couple_members where user_id = :userId")
        .param("userId", userId).query(UUID.class).optional().orElse(null);
    if (coupleId != null) {
      sections.put("couple", rows(
          "select * from couples where id = :coupleId", "coupleId", coupleId));
      sections.put("members", rows("""
          select cm.id, cm.user_id, cm.role, cm.joined_at,
                 u.display_name, u.nickname, u.pronouns, u.birthday, u.photo_url
            from couple_members cm join users u on u.id = cm.user_id
           where cm.couple_id = :coupleId
          """, "coupleId", coupleId));
        for (String table : List.of("stories", "key_dates",
          "calendar_events", "memories", "tags", "albums", "messages")) {
        sections.put(table, rows(
            "select * from " + table + " where couple_id = :coupleId", "coupleId", coupleId));
      }
        sections.put("story_moments", rows("""
          select sm.* from story_moments sm join stories s on s.id = sm.story_id
           where s.couple_id = :coupleId
          """, "coupleId", coupleId));
      sections.put("memory_photos", rows("""
          select p.* from memory_photos p join memories m on m.id = p.memory_id
           where m.couple_id = :coupleId
          """, "coupleId", coupleId));
        sections.put("my_memory_private_notes", rows("""
          select n.* from memory_private_notes n join memories m on m.id = n.memory_id
           where m.couple_id = :coupleId and n.user_id = :userId
          """, Map.of("coupleId", coupleId, "userId", userId)));
      sections.put("memory_tags", rows("""
          select mt.* from memory_tags mt join memories m on m.id = mt.memory_id
           where m.couple_id = :coupleId
          """, "coupleId", coupleId));
      sections.put("album_memories", rows("""
          select am.* from album_memories am join albums a on a.id = am.album_id
           where a.couple_id = :coupleId
          """, "coupleId", coupleId));
      sections.put("message_reactions", rows("""
          select r.* from message_reactions r join messages m on m.id = r.message_id
           where m.couple_id = :coupleId
          """, "coupleId", coupleId));
      sections.put("message_receipts", rows("""
          select r.* from message_receipts r join messages m on m.id = r.message_id
           where m.couple_id = :coupleId
          """, "coupleId", coupleId));
    }
    return new AccountExport(Instant.now().toString(), sections);
  }

  private List<Map<String, Object>> rows(String sql, String parameter, Object value) {
    return jdbc.sql(sql).param(parameter, value).query().listOfRows();
  }

  private List<Map<String, Object>> rows(String sql, Map<String, ?> parameters) {
    var statement = jdbc.sql(sql);
    for (var parameter : parameters.entrySet()) {
      statement.param(parameter.getKey(), parameter.getValue());
    }
    return statement.query().listOfRows();
  }

  private void requireUser(UUID userId) {
    if (users.findById(userId).filter(user -> user.getDeletedAt() == null).isEmpty()) {
      throw AppException.unauthorized("Sign in to continue");
    }
  }

  private DeletionStatus status(AccountDeletionRequest request) {
    return new DeletionStatus(
        request.getCancelledAt() == null && request.getExecutedAt() == null,
        request.getRequestedAt().toString(), request.getExecuteAfter().toString(),
        request.getCancelledAt() == null ? null : request.getCancelledAt().toString(),
        request.getExecutedAt() == null ? null : request.getExecutedAt().toString());
  }

  public record DeletionStatus(
      boolean pending, String requestedAt, String executeAfter, String cancelledAt,
      String executedAt) {}

  public record AccountExport(String generatedAt, Map<String, Object> data) {}
}
