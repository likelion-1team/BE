package com.babylion.team1.domain.workrequest.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProcessingMethod {

	MANUAL("실무자 직접"),
	LLM("LLM 중심"),
	HYBRID("혼합 경로");

	private final String description;
}
