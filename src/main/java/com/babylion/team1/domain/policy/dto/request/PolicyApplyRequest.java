package com.babylion.team1.domain.policy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record PolicyApplyRequest(
		@Schema(description = "적용할 프로젝트 ID. JSON 양의 정수 배열만 허용", example = "[5, 6]")
		@JsonDeserialize(contentUsing = PolicyApplyRequest.ProjectIdDeserializer.class)
		@NotEmpty List<@NotNull @Positive Long> projectIds) {

	public static final class ProjectIdDeserializer extends ValueDeserializer<Long> {
		@Override
		public Long deserialize(JsonParser parser, DeserializationContext context) {
			if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
				return context.reportInputMismatch(Long.class, "프로젝트 ID는 JSON 정수여야 합니다.");
			}
			return parser.getLongValue();
		}
	}
}
