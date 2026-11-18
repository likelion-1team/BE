package com.babylion.team1.domain.policy.dto.request;

import com.babylion.team1.domain.policy.validation.JsonNesting;
import com.babylion.team1.domain.workrequest.entity.RouteType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;

public record PolicyVersionDraftRequest(
		@Schema(description = "기준 버전 ID (동일 정책 소속 검증용, 설정 자동 복사 없음)", example = "10")
		@Positive Long baseVersionId,
		@Schema(description = "새 버전명 (공백 불가, 최대 50자)", example = "v1.2-draft", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank @Size(max = 50) String versionName,
		@Schema(description = "허용 도구 JSON (누락·null 불가, 객체·배열 중첩 최대 100단계, 요청 본문과 마지막 스칼라는 제외, 내부 구조·허용값은 별도 업무 계약)", implementation = Object.class,
				types = {"object", "array", "string", "number", "boolean"}, example = "[\"AI\"]", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull @JsonNesting(max = 100) JsonNode allowedTools,
		@Schema(description = "확신도 저장값 (DECIMAL(5,2): 정수 최대 3자리·소수 최대 2자리, 단위 변환 없음)", example = "85.25")
		@Digits(integer = 3, fraction = 2) BigDecimal confidenceThreshold,
		@Schema(description = "성공 정의 (선택, UTF-8 최대 65535바이트)", example = "검토 통과") String successDefinition,
		@Schema(description = "품질·시간 기준 JSON (누락·null 허용, 객체·배열 중첩 최대 100단계, 요청 본문과 마지막 스칼라는 제외)", implementation = Object.class,
				types = {"object", "array", "string", "number", "boolean", "null"}) @JsonNesting(max = 100) JsonNode qualityTimeCriteria,
		@Schema(description = "예외 조건 JSON (누락·null 허용, 객체·배열 중첩 최대 100단계, 요청 본문과 마지막 스칼라는 제외)", implementation = Object.class,
				types = {"object", "array", "string", "number", "boolean", "null"}) @JsonNesting(max = 100) JsonNode exceptionConditions,
		@Schema(description = "기본 경로 (선택)") RouteType defaultRoute) {

	public PolicyVersionDraftRequest {
		// JsonNode의 JSON null도 @NotNull이 검증할 수 있도록 Java null로 통일한다.
		if (allowedTools != null && (allowedTools.isNull() || allowedTools.isMissingNode())) {
			allowedTools = null;
		}
	}

	@Schema(hidden = true)
	@AssertTrue(message = "successDefinition은 UTF-8 기준 65535바이트 이하여야 합니다.")
	public boolean isSuccessDefinitionSupported() {
		return successDefinition == null || successDefinition.getBytes(StandardCharsets.UTF_8).length <= 65535;
	}
}
