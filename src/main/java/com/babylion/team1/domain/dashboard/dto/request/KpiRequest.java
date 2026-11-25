package com.babylion.team1.domain.dashboard.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * KPI 카드 조회 조건. ME는 from·to, TEAM은 weekStart가 필수다.
 * 값이 잘못됐을 때 400이 아니라 422 VALIDATION_FAILED로 응답하도록 문자열로 받아 서비스에서 검증한다.
 */
public record KpiRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ME", "TEAM"})
        String scope,
        @Schema(description = "ME 필수. 시작일(KST, 포함)", format = "date")
        String from,
        @Schema(description = "ME 필수. 종료일(KST, 포함)", format = "date")
        String to,
        @Schema(description = "TEAM 필수. 완료된 주의 월요일(KST)", format = "date")
        String weekStart,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "분석 기준 버전", type = "integer")
        String settingsVersion
) {
}
