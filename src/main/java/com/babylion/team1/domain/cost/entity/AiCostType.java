package com.babylion.team1.domain.cost.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiCostType {

	CALL("호출비"),
	OPERATION("운영비(배부)"),
	REVIEW("검토비"),
	ERROR_RECOVERY("오류 수습비");

	private final String description;
}
