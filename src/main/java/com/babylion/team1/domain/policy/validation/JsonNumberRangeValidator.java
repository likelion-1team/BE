package com.babylion.team1.domain.policy.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayDeque;
import tools.jackson.databind.JsonNode;

public class JsonNumberRangeValidator implements ConstraintValidator<JsonNumberRange, JsonNode> {
	@Override
	public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		var pending = new ArrayDeque<JsonNode>();
		pending.push(value);
		while (!pending.isEmpty()) {
			var node = pending.pop();
			// 큰 정수도 유한한 double로 변환할 수 있어야 한다. JSON 문자열은 숫자로 해석하지 않는다.
			if (node.isNumber() && !Double.isFinite(node.numberValue().doubleValue())) {
				return false;
			}
			if (node.isObject() || node.isArray()) {
				for (JsonNode child : node) {
					pending.push(child);
				}
			}
		}
		return true;
	}
}
