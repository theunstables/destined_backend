package com.loveos.api.feedback;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, UUID> {}
