package com.babylion.team1.domain.policy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;

public record PolicyRecommendationRejectRequest(
		@Schema(description = "반려 사유 (앞뒤 공백 제거, 공백 불가, UTF-8 최대 65535바이트)",
				example = "현재 운영 기준에 맞지 않습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank String rejectionReason) {

	public PolicyRecommendationRejectRequest {
		rejectionReason = rejectionReason == null ? null : rejectionReason.strip();
	}

	@Schema(hidden = true)
	@AssertTrue(message = "rejectionReason은 UTF-8 기준 65535바이트 이하여야 합니다.")
	public boolean isRejectionReasonSupported() {
		return rejectionReason == null || rejectionReason.getBytes(StandardCharsets.UTF_8).length <= 65535;
	}
}
