package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.dashboard.dto.request.TrendMetric;
import java.time.LocalDate;
import java.util.List;

/**
 * 지표 추이. 팀원이면 본인 값, 팀장이면 팀 집계.
 * metric: 요청한 지표(camelCase), estimated: 추정치 기반 지표인지 여부,
 * onboardingStartDate: 차트 세로선용(미설정이면 null), points: 날짜 오름차순.
 */
public record TrendResponse(
        String metric,
        boolean estimated,
        LocalDate onboardingStartDate,
        List<TrendPoint> points
) {

    public static TrendResponse of(TrendMetric metric, LocalDate onboardingStartDate, List<TrendPoint> points) {
        return new TrendResponse(metric.value(), metric.estimated(), onboardingStartDate, points);
    }
}
