package com.babylion.team1.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleType {

	USER("일반 사용자"),
	ADMIN("관리자"),
	APPROVER("승인 책임자");

	private final String description;
}
