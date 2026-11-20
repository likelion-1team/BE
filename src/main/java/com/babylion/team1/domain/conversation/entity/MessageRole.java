package com.babylion.team1.domain.conversation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageRole {

	USER("사용자"),
	ASSISTANT("AI 답변");

	private final String description;
}
