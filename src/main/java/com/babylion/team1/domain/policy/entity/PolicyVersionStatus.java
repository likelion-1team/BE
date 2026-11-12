package com.babylion.team1.domain.policy.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PolicyVersionStatus {

	DRAFT("초안"),
	ACTIVE("적용 중"),
	ROLLED_BACK("롤백됨");

	private final String description;
}
