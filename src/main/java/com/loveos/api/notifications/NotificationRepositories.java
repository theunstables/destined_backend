package com.loveos.api.notifications;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreference, UUID> {}

interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {
  Optional<DeviceToken> findByToken(String token);
}
