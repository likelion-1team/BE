package com.babylion.team1.domain.feedback.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReactionType {

	HELPFUL("도움됨"),
	NOT_APPLICABLE("해당 없음");

	private final String description;
}
