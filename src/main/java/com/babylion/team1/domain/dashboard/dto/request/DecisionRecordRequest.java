package com.babylion.team1.domain.dashboard.dto.request;

import com.babylion.team1.domain.policy.entity.DecisionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 대시보드 결과 기록 저장 요청.
 * policyId는 받지 않고 recommendationId로 서버에서 조회한다.
 */
public record DecisionRecordRequest(
		@Schema(description = "정책 추천 ID", example = "10")
		@NotNull(message = "recommendationId는 필수입니다.")
		Long recommendationId,

		@Schema(description = "최종 결정 (EXPAND, IMPROVE, MAINTAIN, HOLD)", example = "EXPAND")
		@NotNull(message = "decision은 필수입니다.")
		DecisionResult decision,

		@Schema(description = "결정 사유. 시스템 제안과 다른 결정일 때 필수",
				example = "3관문 우수 통과 및 실무진 만족도 고평가로 인한 전사 확대 결정")
		String reason
) {
}
