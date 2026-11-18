package com.babylion.team1.domain.policy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PolicyVersionHistoryRequest(
		@Schema(description = "0부터 시작하는 페이지", defaultValue = "0") @Min(0) Integer page,
		@Schema(description = "페이지 크기 (1~100)", defaultValue = "20") @Min(1) @Max(100) Integer size) {

	public PolicyVersionHistoryRequest {
		page = page == null ? 0 : page;
		size = size == null ? 20 : size;
	}

	@Schema(hidden = true)
	@AssertTrue(message = "page와 size로 계산한 조회 시작 위치가 너무 큽니다.")
	public boolean isOffsetSupported() {
		return (long) page * size <= Integer.MAX_VALUE;
	}
}
