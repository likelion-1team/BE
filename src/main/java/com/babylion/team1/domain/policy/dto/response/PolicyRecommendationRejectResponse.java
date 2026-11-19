package com.babylion.team1.domain.policy.dto.response;

import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import java.time.LocalDateTime;

public record PolicyRecommendationRejectResponse(Long recommendationId, RecommendationStatus status, String rejectionReason,
		Long decidedBy, LocalDateTime decidedAt) {
}
