package com.loveos.api.media;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaObjectRepository extends JpaRepository<MediaObject, String> {
  List<MediaObject> findByCoupleId(UUID coupleId);
}
