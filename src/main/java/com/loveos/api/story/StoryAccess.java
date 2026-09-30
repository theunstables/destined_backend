package com.loveos.api.story;

import java.time.LocalDate;
import java.util.UUID;

/** Read-only Story projection used by composing features. */
public interface StoryAccess {
  LocalDate metDate(UUID userId);
}