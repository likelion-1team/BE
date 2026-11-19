package com.babylion.team1.domain.policy.dto.response;

import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record PolicyRecommendationListResponse(List<Item> items, int page, int size, long totalElements) {
	@Schema(name = "PolicyRecommendationListItem")
	public record Item(Long recommendationId, Long policyId, String taskType, RecommendationStatus status,
			String observedProblem, Integer sampleCount, LocalDateTime createdAt) {
	}
}
