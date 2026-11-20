package com.babylion.team1.domain.team.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TeamRole {

	LEADER("팀장"),
	MEMBER("팀원");

	private final String description;
}
