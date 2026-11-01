package com.loveos.api.media;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {
  Optional<MediaAsset> findByObjectKeyAndCoupleIdAndStorageDriver(String key, UUID coupleId, String driver);
}