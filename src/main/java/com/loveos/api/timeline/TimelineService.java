package com.loveos.api.timeline;

import com.loveos.api.core.AppException;
import com.loveos.api.core.DateUtils;
import com.loveos.api.core.ErrorCode;
import com.loveos.api.pairing.CoupleAccess;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineService {

  private static final String PROJECTION = """
      with timeline as (
        select id as item_id, date as occurred_on, 'memory'::text as item_type,
               title, coalesce(caption, location) as subtitle,
               (select url from memory_photos p where p.memory_id = memories.id
                 order by position limit 1) as media_url
          from memories where couple_id = :coupleId and deleted_at is null
        union all
        select id, date, 'calendar', title, coalesce(location, notes), null
          from calendar_events where couple_id = :coupleId and deleted_at is null
        union all
        select sm.id, sm.date, 'story',
               case sm.kind when 'FIRST_DATE' then 'First date'
                    when 'BECAME_US' then 'Became us' else 'First memory' end,
               coalesce(sm.location, sm.note), sm.photo_url
          from story_moments sm join stories s on s.id = sm.story_id
         where s.couple_id = :coupleId and sm.date is not null
        union all
        select id, date, 'occasion', replace(initcap(kind), '_', ' '), null, null
          from key_dates where couple_id = :coupleId
      )
      select item_id, occurred_on, item_type, title, subtitle, media_url
        from timeline
      """;

  private final CoupleAccess couples;
  private final JdbcClient jdbc;

  TimelineService(CoupleAccess couples, JdbcClient jdbc) {
    this.couples = couples;
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Page list(UUID userId, String rawCursor, int limit) {
    UUID coupleId = couples.requireMembership(userId).coupleId();
    Cursor cursor = decode(rawCursor);
    String cursorClause = cursor == null ? """
         order by occurred_on desc, item_type desc, item_id desc limit :fetch
        """ : """
         where (occurred_on, item_type, item_id) < (:date, :type, :id)
         order by occurred_on desc, item_type desc, item_id desc limit :fetch
        """;
    var query = jdbc.sql(PROJECTION + cursorClause)
        .param("coupleId", coupleId).param("fetch", limit + 1);
    if (cursor != null) {
      query = query.param("date", cursor.date()).param("type", cursor.type())
          .param("id", cursor.id());
    }
    List<Item> loaded = query.query((rs, row) -> new Item(
        rs.getObject("item_id", UUID.class).toString(), rs.getString("item_type"),
        rs.getObject("occurred_on", LocalDate.class).toString(), rs.getString("title"),
        rs.getString("subtitle"), rs.getString("media_url"))).list();
    boolean more = loaded.size() > limit;
    List<Item> items = more ? loaded.subList(0, limit) : loaded;
    String next = more && !items.isEmpty() ? encode(items.get(items.size() - 1)) : null;
    return new Page(List.copyOf(items), next);
  }

  private static Cursor decode(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
          .split("\\|", -1);
      if (parts.length != 3) throw new IllegalArgumentException();
      return new Cursor(DateUtils.parseCalendarDate(parts[0]), parts[1], UUID.fromString(parts[2]));
    } catch (RuntimeException exception) {
      throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid timeline cursor");
    }
  }

  private static String encode(Item item) {
    String value = item.date() + "|" + item.type() + "|" + item.id();
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  public record Item(
      String id, String type, String date, String title, String subtitle, String mediaUrl) {}
  public record Page(List<Item> items, String nextCursor) {}
  private record Cursor(LocalDate date, String type, UUID id) {}
}
