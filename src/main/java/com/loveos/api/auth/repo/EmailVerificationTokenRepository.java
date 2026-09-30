package com.loveos.api.auth.repo;

import com.loveos.api.auth.domain.EmailVerificationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, UUID> {

  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  /**
   * Clears outstanding tokens before a new one is issued.
   *
   * <p>Requesting a fresh verification email should invalidate the previous
   * link: several live links for one address widens the window in which an old
   * email — forwarded, or sitting in a shared inbox — still works.
   */
  void deleteAllByUserId(UUID userId);
}
