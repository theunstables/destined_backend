package com.loveos.api.auth.repo;

import com.loveos.api.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Users.
 *
 * <p>Lookup is by email in lower case. Normalisation happens in the service
 * before the call rather than here, so that there is exactly one place where an
 * address is canonicalised — a repository that silently lower-cased its argument
 * would hide the fact that the stored value is normalised too, and the two could
 * drift apart.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  /** Serializes pairing decisions made for the same account. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select user from User user where user.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
