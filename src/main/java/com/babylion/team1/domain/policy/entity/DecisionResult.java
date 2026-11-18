package com.babylion.team1.domain.policy.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 책임자의 AI 활용 최종 결정 */
@Getter
@RequiredArgsConstructor
public enum DecisionResult {

	EXPAND("확대"),
	IMPROVE("개선"),
	MAINTAIN("유지"),
	HOLD("보류");

	private final String description;
}
