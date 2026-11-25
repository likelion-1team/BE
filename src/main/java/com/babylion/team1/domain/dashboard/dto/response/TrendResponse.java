package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.dashboard.dto.request.DashboardScope;
import java.time.OffsetDateTime;
import java.util.List;

/** 지표 추이. points는 날짜 오름차순이며 TEAM이면 한 개다. */
public record TrendResponse(
        DashboardScope scope,
        String cohortId,
        OffsetDateTime asOf,
        int metricDefinitionVersion,
        List<TrendPoint> points
) {
}
