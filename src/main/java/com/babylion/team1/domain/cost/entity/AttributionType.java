package com.babylion.team1.domain.cost.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AttributionType {

	DIRECT("직접 귀속"),
	ALLOCATED("공통비 배부"),
	UNALLOCATED("미배부");

	private final String description;
}
