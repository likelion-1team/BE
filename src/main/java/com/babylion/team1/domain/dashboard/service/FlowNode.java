package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.workrequest.entity.RouteType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 경로 흐름 차트 노드.
 * 응답의 code는 enum 이름, label은 UI 표시용 문자열이다.
 * 2단계 노드는 Work_Request.route_type 값과 1:1로 대응한다.
 */
@Getter
@RequiredArgsConstructor
public enum FlowNode {

	RECEIPT("접수", null),
	RULE_CONFIRM("규칙 확정", RouteType.RULE),
	DECISION_AI("판단형 AI", null),
	ERP_DIRECT("ERP 직결", RouteType.AI_ERP_DIRECT),
	LLM("LLM", RouteType.LLM),
	ADD_QUESTION("추가 질문", RouteType.ADDITIONAL_QUESTION),
	REVIEW("담당자 검토", RouteType.MANUAL_REVIEW);

	private final String label;
	private final RouteType routeType;
}
