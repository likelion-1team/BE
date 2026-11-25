package com.babylion.team1.domain.dashboard.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 필터 옵션 조회 조건. 값 검증은 서비스에서 하며 잘못되면 422 VALIDATION_FAILED. */
public record FilterOptionsRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ME", "TEAM"})
        String scope
) {
}
