package com.babylion.team1.domain.policy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = JsonNestingValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
		ElementType.ANNOTATION_TYPE, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonNesting {
	String message() default "JSON의 객체·배열 중첩은 {max}단계 이하여야 합니다.";
	int max() default 100;
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
}
