package com.babylion.team1.domain.policy.repository;

import com.babylion.team1.domain.policy.entity.PolicyRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRecommendationRepository extends JpaRepository<PolicyRecommendation, Long> {
}
