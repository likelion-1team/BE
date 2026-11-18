package com.babylion.team1.domain.policy.dto.request;

import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PolicyListRequest(
		@Schema(description = "업무 유형 (앞뒤 공백 제거, 빈 값은 전체 조회)")
		@Size(max = 50) String taskType,
		@Schema(description = "버전 상태 (생략 시 ACTIVE, ROLLED_BACK)") PolicyVersionStatus status,
		@Schema(description = "0부터 시작하는 페이지", defaultValue = "0") @Min(0) Integer page,
		@Schema(description = "페이지 크기 (1~100)", defaultValue = "20") @Min(1) @Max(100) Integer size) {

	public PolicyListRequest {
		taskType = taskType == null || taskType.isBlank() ? null : taskType.strip();
		page = page == null ? 0 : page;
		size = size == null ? 20 : size;
	}

	public List<PolicyVersionStatus> statuses() {
		return status == null
				? List.of(PolicyVersionStatus.ACTIVE, PolicyVersionStatus.ROLLED_BACK)
				: List.of(status);
	}

	/** JPA의 조회 시작 위치는 int 범위이므로 큰 페이지 입력을 500 대신 검증 오류로 처리한다. */
	@Schema(hidden = true)
	@AssertTrue(message = "page와 size로 계산한 조회 시작 위치가 너무 큽니다.")
	public boolean isOffsetSupported() {
		return (long) page * size <= Integer.MAX_VALUE;
	}
}
