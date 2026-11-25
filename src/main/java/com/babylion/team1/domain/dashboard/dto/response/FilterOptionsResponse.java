package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.dashboard.dto.request.DashboardScope;
import com.babylion.team1.domain.dashboard.dto.request.KpiUnit;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.global.policy.ServerPolicy;
import java.time.LocalDate;
import java.util.List;

/**
 * 대시보드 필터 옵션. settingsVersions 중 하나를 KPI 카드·추이의 settingsVersion으로 보낸다.
 * 모델 필터와 온보딩 비교는 P0에서 제공하지 않아 allowedModels는 빈 목록, comparisonAvailable은 false로 고정한다.
 */
public record FilterOptionsResponse(
        DashboardScope scope,
        List<TaskType> taskTypes,
        List<Integer> settingsVersions,
        List<KpiUnit> allowedUnits,
        List<Week> availableWeeks,
        List<String> allowedModels,
        int policyVersion,
        boolean comparisonAvailable
) {

    public static FilterOptionsResponse of(DashboardScope scope, List<TaskType> taskTypes,
            List<Integer> settingsVersions, List<Week> availableWeeks) {
        List<KpiUnit> allowedUnits = scope == DashboardScope.ME
                ? List.of(KpiUnit.DAY, KpiUnit.WEEK)
                : List.of(KpiUnit.WEEK);
        return new FilterOptionsResponse(scope, taskTypes, settingsVersions, allowedUnits, availableWeeks, List.of(),
                ServerPolicy.VERSION, false);
    }

    public record TaskType(Long taskTypeId, String name) {

        public static TaskType from(TeamTaskType taskType) {
            return new TaskType(taskType.getId(), taskType.getName());
        }
    }

    /** 완료된 KST 주(월~일) */
    public record Week(LocalDate weekStart, LocalDate weekEnd) {

        public static Week startingAt(LocalDate monday) {
            return new Week(monday, monday.plusDays(6));
        }
    }
}
