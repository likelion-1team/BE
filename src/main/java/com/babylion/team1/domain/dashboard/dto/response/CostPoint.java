package com.babylion.team1.domain.dashboard.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 누적 막대 하나. date: DAY면 그날, WEEK면 그 주의 월요일.
 * items: COST_TYPE이면 항상 CALL·REVIEW·REWORK 3개(없으면 0), MODEL·TASK_TYPE이면 데이터가 있는 키만 이름순.
 */
public record CostPoint(
        LocalDate date,
        List<CostItem> items
) {
}
