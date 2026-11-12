package com.babylion.team1.domain.workrequest.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkRequestStatus {

	RECEIVED("접수"),
	IN_PROGRESS("처리 중"),
	PENDING_REVIEW("담당자 검토 대기"),
	COMPLETED("완료"),
	FAILED("실패");

	private final String description;
}
