package com.loveos.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "apple_auth_flows")
@Getter
@Setter
@NoArgsConstructor
public class AppleAuthFlow {
  @Id @Column(name = "state_hash", length = 64) private String stateHash;
  @Column(nullable = false, length = 128) private String nonce;
  @Column(name = "exchange_secret_hash", nullable = false, length = 64) private String exchangeSecretHash;
  @Column(name = "return_uri", nullable = false, length = 1024) private String returnUri;
  @Column(name = "link_user_id") private UUID linkUserId;
  @Column(name = "callback_used", nullable = false) private boolean callbackUsed;
  @Column(name = "ticket_hash", length = 64, unique = true) private String ticketHash;
  @Column(length = 255) private String subject;
  @Column(length = 254) private String email;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
}