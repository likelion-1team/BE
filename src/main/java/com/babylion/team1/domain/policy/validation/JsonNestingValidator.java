package com.babylion.team1.domain.policy.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import tools.jackson.databind.JsonNode;

public class JsonNestingValidator implements ConstraintValidator<JsonNesting, JsonNode> {
	private int max;

	@Override
	public void initialize(JsonNesting constraint) {
		max = constraint.max();
	}

	@Override
	public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
		return withinLimit(value, 0);
	}

	private boolean withinLimit(JsonNode value, int parentNesting) {
		if (value == null || (!value.isObject() && !value.isArray())) {
			return true;
		}
		// MySQL JSON 저장 한계는 객체·배열만 센다. 마지막 스칼라는 중첩에 포함하지 않는다.
		int nesting = parentNesting + 1;
		if (nesting > max) {
			return false;
		}
		for (JsonNode child : value) {
			if (!withinLimit(child, nesting)) {
				return false;
			}
		}
		return true;
	}
}
