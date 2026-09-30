package com.loveos.api.auth.repo;

import com.loveos.api.auth.domain.PasswordResetToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /** A new reset request invalidates any earlier one — see the sibling repository. */
  void deleteAllByUserId(UUID userId);
}
