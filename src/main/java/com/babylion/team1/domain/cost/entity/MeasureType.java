package com.babylion.team1.domain.cost.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MeasureType {

	MEASURED("실측"),
	ESTIMATED("추정");

	private final String description;
}
