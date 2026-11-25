package com.babylion.team1.domain.dashboard.dto.response;

import java.time.LocalDate;

/** 추이 차트의 점 하나. periodStart: DAY면 그날, WEEK면 그 주의 월요일 */
public record TrendPoint(
        LocalDate periodStart,
        MetricResponse metric
) {
}
