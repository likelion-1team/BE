package com.babylion.team1.domain.policy.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RecommendationStatus {

	PENDING("대기"),
	APPROVED("승인"),
	REJECTED("반려");

	private final String description;
}
