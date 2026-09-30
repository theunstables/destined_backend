package com.loveos.api.story.repo;

import com.loveos.api.story.domain.StoryMoment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryMomentRepository extends JpaRepository<StoryMoment, UUID> {

  List<StoryMoment> findByStoryId(UUID storyId);

  void deleteByStoryId(UUID storyId);
}