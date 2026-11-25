package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.dashboard.dto.request.DashboardScope;
import com.babylion.team1.domain.team.entity.TeamGoal;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** KPI 카드. TEAM에서 숨긴 지표가 하나라도 있으면 coverage는 null이다. */
public record KpiResponse(
        DashboardScope scope,
        String cohortId,
        OffsetDateTime asOf,
        int metricDefinitionVersion,
        int policyVersion,
        int settingsVersion,
        Goals goals,
        Coverage coverage,
        List<MetricResponse> metrics
) {

    /** 팀 목표의 현재 값. 설정하지 않았으면 version은 0, 값은 모두 null */
    public record Goals(
            int version,
            BigDecimal successRateMin,
            BigDecimal errorReworkRateMax,
            Integer tokensPerSuccessMax
    ) {

        public static Goals from(TeamGoal goal) {
            return new Goals(goal.getVersion(), goal.getSuccessRateMin(), goal.getErrorReworkRateMax(),
                    goal.getTokensPerSuccessMax());
        }

        public static Goals unset() {
            return new Goals(0, null, null, null);
        }
    }

    /**
     * 집계 범위의 대화 수. selectedCount = inScopeCount + excludedCount + pendingCount + failedCount.
     * 확인·측정 수는 inScopeCount 안에서 센다.
     */
    public record Coverage(
            long selectedCount,
            long inScopeCount,
            long excludedCount,
            long pendingCount,
            long failedCount,
            long outcomeConfirmedCount,
            long reworkConfirmedCount,
            long tokenMeasuredCount
    ) {
    }
}
