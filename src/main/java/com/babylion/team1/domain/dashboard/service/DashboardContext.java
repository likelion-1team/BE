package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.KpiCondition;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 대시보드 조회 한 번에 필요한 공통 정보: 팀, 집계 대상 userId, 팀 설정.
 * scopeUserId가 null이면 팀 전체 집계, 값이 있으면 그 사람 대화만 집계한다.
 * 팀 설정이 없으면 기본값을 쓴다.
 */
record DashboardContext(
        Long teamId,
        Long scopeUserId,
        Optional<TeamSettings> settings
) {

    boolean isTeamScope() {
        return scopeUserId == null;
    }

    KpiCondition condition(List<Long> taskTypeIds, List<String> models) {
        return new KpiCondition(teamId, scopeUserId, taskTypeIds, models);
    }

    /**
     * 입력하지 않은 쪽은 팀 대상 기간으로 채운다.
     * 그래도 비어 있으면 400 TARGET_NOT_SET, from > to면 400 INVALID_REQUEST
     */
    DatePeriod period(LocalDate requestFrom, LocalDate requestTo) {
        LocalDate from = requestFrom != null
                ? requestFrom
                : settings.map(TeamSettings::getTargetFrom).orElse(null);
        LocalDate to = requestTo != null
                ? requestTo
                : settings.map(TeamSettings::getTargetTo).orElse(null);
        if (from == null || to == null) {
            throw new CustomException(ErrorCode.TARGET_NOT_SET);
        }
        return new DatePeriod(from, to);
    }

    int hourlyLaborCost() {
        return settings.map(TeamSettings::getHourlyLaborCost)
                .orElse(TeamSettings.DEFAULT_HOURLY_LABOR_COST);
    }

    int minDailyConversations() {
        return settings.map(TeamSettings::getMinDailyConversations)
                .orElse(TeamSettings.DEFAULT_MIN_DAILY_CONVERSATIONS);
    }

    LocalDate onboardingStartDate() {
        return settings.map(TeamSettings::getOnboardingStartDate).orElse(null);
    }

    Integer targetCount() {
        return settings.map(TeamSettings::getTargetCount).orElse(null);
    }
}
