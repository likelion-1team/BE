package com.babylion.team1.domain.policy.dto.response;

import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import java.time.LocalDateTime;

public record PolicyRecommendationApproveResponse(Long recommendationId, RecommendationStatus status, Long decidedBy,
		LocalDateTime decidedAt, Long policyId, Long versionId, PolicyVersionStatus versionStatus) {
}
