package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.policy.entity.DecisionResult;
import java.time.OffsetDateTime;

/** 대시보드 결과 기록 저장 응답 */
public record DecisionRecordResponse(
		Long auditId,
		Long recommendationId,
		DecisionResult decision,
		OffsetDateTime decidedAt
) {
}
