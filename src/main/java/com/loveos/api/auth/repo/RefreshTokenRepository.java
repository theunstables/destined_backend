package com.loveos.api.auth.repo;

import com.loveos.api.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /** The only lookup there is: the caller presents a token, never an id. */
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Every live session for a user.
   *
   * <p>Used by reuse detection and by password reset, both of which need to end
   * all of them at once. Returned rather than bulk-updated so the caller can log
   * how many were killed — a silent mass revocation is exactly the event you
   * want a record of.
   */
  List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);

  /**
   * Revokes a user's entire token family in one statement.
   *
   * <p>{@code @Modifying} with {@code clearAutomatically} because a bulk update
   * bypasses the persistence context: entities already loaded in this
   * transaction would otherwise keep their stale {@code revokedAt} and could be
   * flushed back over the change.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update RefreshToken t set t.revokedAt = :now "
      + "where t.userId = :userId and t.revokedAt is null")
  int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

  /**
   * Housekeeping: expired rows are dead weight and a growing table slows the
   * unique-index lookup that every refresh depends on.
   */
  @Modifying
  @Query("delete from RefreshToken t where t.expiresAt < :before")
  int deleteExpiredBefore(@Param("before") Instant before);
}
