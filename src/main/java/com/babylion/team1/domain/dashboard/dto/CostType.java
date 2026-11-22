package com.babylion.team1.domain.dashboard.dto;

/**
 * 비용 종류. groupBy=COST_TYPE일 때 응답 key로 쓰인다.
 * CALL: 호출비(estimated_cost, 단가 없는 모델은 0), REVIEW: 검토비, REWORK: 수습비(시간 x 시간당 인건비).
 */
public enum CostType {
    CALL,
    REVIEW,
    REWORK
}
