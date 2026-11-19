package com.babylion.team1.domain.policy.dto.request;

import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record PolicyRecommendationListRequest(
		@Schema(description = "정책 ID 필터 (생략 시 회사 전체, 미존재·타사는 빈 목록)") @Positive Long policyId,
		@Schema(description = "추천 상태 (생략 시 전체 상태)") RecommendationStatus status,
		@Schema(description = "0부터 시작하는 페이지", defaultValue = "0") @Min(0) Integer page,
		@Schema(description = "페이지 크기 (1~100)", defaultValue = "20") @Min(1) @Max(100) Integer size) {

	public PolicyRecommendationListRequest {
		page = page == null ? 0 : page;
		size = size == null ? 20 : size;
	}

	@Schema(hidden = true)
	@AssertTrue(message = "page와 size로 계산한 조회 시작 위치가 너무 큽니다.")
	public boolean isOffsetSupported() {
		return (long) page * size <= Integer.MAX_VALUE;
	}
}
