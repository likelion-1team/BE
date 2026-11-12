package com.babylion.team1.domain.workrequest.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RouteType {

	RULE("규칙 확정"),
	AI_ERP_DIRECT("판단형 AI → ERP 직결"),
	LLM("LLM"),
	ADDITIONAL_QUESTION("추가 질문"),
	MANUAL_REVIEW("담당자 검토");

	private final String description;
}
