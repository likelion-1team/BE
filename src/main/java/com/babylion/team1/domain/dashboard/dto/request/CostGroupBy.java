package com.babylion.team1.domain.dashboard.dto.request;

/**
 * 비용 구조를 나누는 기준.
 * COST_TYPE: 호출비·검토비·수습비(CostType), MODEL: 모델명(NULL이면 UNKNOWN), TASK_TYPE: 업무 유형명.
 */
public enum CostGroupBy {
    COST_TYPE,
    MODEL,
    TASK_TYPE
}
