package com.babylion.team1.domain.policy.dto.response;

import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.workrequest.entity.RouteType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

public record PolicyVersionDetailResponse(Long policyId, String taskType, Long versionId, String versionName,
		PolicyVersionStatus status, BigDecimal confidenceThreshold,
		@Schema(description = "저장된 JSON 설정 (배열·객체 등 원본 구조)", implementation = Object.class,
				types = {"object", "array", "string", "number", "boolean", "null"}) JsonNode allowedTools,
		String successDefinition,
		@Schema(description = "저장된 품질·시간 기준 JSON 또는 null", implementation = Object.class,
				types = {"object", "array", "string", "number", "boolean", "null"}) JsonNode qualityTimeCriteria,
		@Schema(description = "저장된 예외 조건 JSON 또는 null", implementation = Object.class,
				types = {"object", "array", "string", "number", "boolean", "null"}) JsonNode exceptionConditions,
		RouteType defaultRoute, Long createdBy, LocalDateTime createdAt, Long approvedBy, LocalDateTime appliedAt,
		LocalDateTime rolledBackAt, String rollbackReason) {
}
