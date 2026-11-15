package com.babylion.team1.domain.cost.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SettlementStatus {

	SETTLED("결산 완료"),
	UNSETTLED("미결산");

	private final String description;
}
