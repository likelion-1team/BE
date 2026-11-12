package com.babylion.team1.domain.audit.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditEventType {

	LOGIN_SUCCESS("로그인 성공"),
	LOGIN_FAILURE("로그인 실패"),
	LOGOUT("로그아웃"),
	PERMISSION_DENIED("권한 위반"),
	POLICY_CREATED("정책 초안 생성"),
	POLICY_APPROVED("정책 적용(승인)"),
	POLICY_ROLLED_BACK("정책 롤백"),
	RECOMMENDATION_APPROVED("정책 추천 승인"),
	RECOMMENDATION_REJECTED("정책 추천 반려"),
	DECISION_RECORDED("AI 활용 결정 기록");

	private final String description;
}
