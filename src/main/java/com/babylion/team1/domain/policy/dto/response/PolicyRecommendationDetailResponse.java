package com.babylion.team1.domain.policy.dto.response;

import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

public record PolicyRecommendationDetailResponse(Long recommendationId, Long policyId, RecommendationStatus status,
		String observedProblem, String causeCandidate, String nextAction,
		@Schema(description = "저장된 JSON 구조 또는 null (내부 키·타입은 생산 계약에 따름)", implementation = Object.class,
				types = {"object", "array", "string", "number", "boolean", "null"}) JsonNode detail,
		Integer sampleCount, String rejectionReason, Long decidedBy, LocalDateTime decidedAt, LocalDateTime createdAt) {
}
