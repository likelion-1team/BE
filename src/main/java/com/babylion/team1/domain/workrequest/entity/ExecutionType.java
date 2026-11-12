package com.babylion.team1.domain.workrequest.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExecutionType {

	RULE("규칙 확정"),
	AI_JUDGMENT("판단형 AI"),
	LLM("LLM");

	private final String description;
}
