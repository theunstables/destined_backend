package com.loveos.api.pairing.repo;

import com.loveos.api.pairing.domain.Invite;
import com.loveos.api.pairing.domain.InviteStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select invite from Invite invite where invite.code = :code")
  Optional<Invite> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invite> findFirstByRedeemedByIdAndCreatedByIdAndStatusOrderByRedeemedAtDesc(
            UUID redeemedById, UUID createdById, InviteStatus status);

  Optional<Invite> findFirstByRedeemedByIdAndStatusOrderByRedeemedAtDesc(
      UUID redeemedById, InviteStatus status);

  Optional<Invite> findFirstByCoupleIdAndStatusOrderByCreatedAtDesc(
      UUID coupleId, InviteStatus status);

  List<Invite> findByCoupleIdAndStatusIn(UUID coupleId, List<InviteStatus> statuses);

    boolean existsByCode(String code);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update Invite invite
         set invite.status = com.loveos.api.pairing.domain.InviteStatus.CANCELLED,
             invite.cancelledAt = :now
       where invite.coupleId = :coupleId
         and invite.status in :statuses
      """)
  int cancelByCoupleIdAndStatusIn(
      @Param("coupleId") UUID coupleId,
      @Param("statuses") List<InviteStatus> statuses,
      @Param("now") java.time.Instant now);

}