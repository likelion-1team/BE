package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import java.time.LocalDate;

/**
 * 대시보드 KPI 카드 응답.
 * 팀원이면 본인 지표, 팀장이면 팀 전체 집계. coverage는 팀장에게만 내려가고 팀원이면 null.
 */
public record KpiResponse(
        LocalDate from,
        LocalDate to,
        LocalDate prevFrom,
        LocalDate prevTo,
        MetricValue tokensPerSuccess,
        MetricValue reworkRate,
        MetricValue successRate,
        MetricValue costPerSuccess,
        MetricValue savingAmount,
        MetricValue humanTime,
        MetricValue conversationCount,
        CoverageResponse coverage
) {

    public static KpiResponse of(DatePeriod period, DatePeriod prevPeriod, MetricValue tokensPerSuccess,
            MetricValue reworkRate, MetricValue successRate, MetricValue costPerSuccess, MetricValue savingAmount,
            MetricValue humanTime, MetricValue conversationCount, CoverageResponse coverage) {
        return new KpiResponse(period.from(), period.to(), prevPeriod.from(), prevPeriod.to(),
                tokensPerSuccess, reworkRate, successRate, costPerSuccess, savingAmount, humanTime,
                conversationCount, coverage);
    }
}
