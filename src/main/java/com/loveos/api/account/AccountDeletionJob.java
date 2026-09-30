package com.loveos.api.account;

import com.loveos.api.auth.repo.UserRepository;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tombstones due accounts and credentials while retaining couple-owned history. */
@Component
class AccountDeletionJob {

  private final AccountDeletionRequestRepository requests;
  private final UserRepository users;
  private final JdbcClient jdbc;

  AccountDeletionJob(
      AccountDeletionRequestRepository requests, UserRepository users, JdbcClient jdbc) {
    this.requests = requests;
    this.users = users;
    this.jdbc = jdbc;
  }

  @Scheduled(fixedDelayString = "${loveos.lifecycle.deletion-scan-delay:PT5M}")
  @Transactional
  void executeDueRequests() {
    Instant now = Instant.now();
    for (var request : requests
        .findByCancelledAtIsNullAndExecutedAtIsNullAndExecuteAfterLessThanEqual(now)) {
      users.findByIdForUpdate(request.getUserId()).ifPresent(user -> {
        user.setEmail("deleted+" + user.getId() + "@deleted.invalid");
        user.setEmailVerified(false);
        user.setPasswordHash(null);
        user.setDisplayName("Deleted member");
        user.setNickname(null);
        user.setPronouns(null);
        user.setBirthday(null);
        user.setPhotoUrl(null);
        user.setDeletedAt(now);
      });
      for (String table : new String[] {
          "refresh_tokens", "email_verification_tokens", "password_reset_tokens"}) {
        jdbc.sql("delete from " + table + " where user_id = :userId")
            .param("userId", request.getUserId()).update();
      }
      jdbc.sql("update device_tokens set active = false where user_id = :userId")
          .param("userId", request.getUserId()).update();
      request.setExecutedAt(now);
    }
  }
}