package com.babylion.team1.domain.policy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record PolicyVersionDiffResponse(Long policyId, Long baseId, Long targetId, List<Change> changes) {
	@Schema(name = "PolicyVersionDiffChange")
	public record Change(String field,
			@Schema(description = "기준 버전의 값", implementation = Object.class,
					types = {"object", "array", "string", "number", "boolean", "null"}) JsonNode baseValue,
			@Schema(description = "비교 버전의 값", implementation = Object.class,
					types = {"object", "array", "string", "number", "boolean", "null"}) JsonNode targetValue) {
	}
}
