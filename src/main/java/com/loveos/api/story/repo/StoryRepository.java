package com.loveos.api.story.repo;

import com.loveos.api.story.domain.Story;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryRepository extends JpaRepository<Story, UUID> {

  Optional<Story> findByCoupleId(UUID coupleId);
}