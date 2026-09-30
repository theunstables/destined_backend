package com.loveos.api.messaging.repo;

import com.loveos.api.messaging.domain.Message;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, UUID> {
  Optional<Message> findByIdAndCoupleIdAndDeletedAtIsNull(UUID id, UUID coupleId);

  Optional<Message> findByCoupleIdAndClientId(UUID coupleId, String clientId);

  List<Message> findByCoupleIdAndDeletedAtIsNullOrderBySentAtDescIdDesc(
      UUID coupleId, Pageable pageable);

  @Query(value = """
      select * from messages
      where couple_id = :coupleId and deleted_at is null
        and (sent_at, id) < (:cursorTime, :cursorId)
      order by sent_at desc, id desc
      """, nativeQuery = true)
  List<Message> pageBefore(
      UUID coupleId, Instant cursorTime, UUID cursorId, Pageable pageable);

  List<Message> findByCoupleIdAndPinnedTrueAndDeletedAtIsNullOrderBySentAtDesc(
      UUID coupleId, Pageable pageable);

  @Query("""
      select m from Message m
      where m.coupleId = :coupleId and m.deletedAt is null
        and lower(m.body) like lower(concat('%', :query, '%'))
      order by m.sentAt desc, m.id desc
      """)
  List<Message> search(UUID coupleId, String query, Pageable pageable);
}
