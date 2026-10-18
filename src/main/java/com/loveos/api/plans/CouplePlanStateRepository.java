package com.loveos.api.plans;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CouplePlanStateRepository extends JpaRepository<CouplePlanState, UUID> {}
