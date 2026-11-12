package com.babylion.team1.domain.budget.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VerificationStatus {

	PENDING("검증 대기"),
	VERIFIED("검증 완료"),
	FAILED("검증 실패");

	private final String description;
}
