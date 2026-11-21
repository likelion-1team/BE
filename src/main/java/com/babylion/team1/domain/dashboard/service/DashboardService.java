package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.ConversationAggregate;
import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.KpiCondition;
import com.babylion.team1.domain.dashboard.dto.request.KpiRequest;
import com.babylion.team1.domain.dashboard.dto.response.CoverageResponse;
import com.babylion.team1.domain.dashboard.dto.response.KpiResponse;
import com.babylion.team1.domain.dashboard.dto.response.MetricValue;
import com.babylion.team1.domain.dashboard.repository.DashboardQueryRepository;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamSettingsRepository teamSettingsRepository;
    private final DashboardQueryRepository dashboardQueryRepository;

    /**
     * 팀원이면 본인 지표, 팀장이면 팀 전체 집계를 돌려준다. coverage는 팀장에게만 채운다.
     * 팀이 없으면 404 TEAM_NOT_FOUND, 팀 멤버가 아니면 403 NOT_A_MEMBER.
     */
    public KpiResponse getKpis(Long teamId, Long userId, KpiRequest request) {
        TeamMember member = getMember(teamId, userId);
        Optional<TeamSettings> settings = teamSettingsRepository.findByTeamId(teamId);

        DatePeriod period = resolvePeriod(request, settings);
        DatePeriod prevPeriod = period.previous();
        KpiCondition condition = new KpiCondition(
                teamId,
                member.isLeader() ? null : userId,
                request.taskTypes(),
                request.models());

        ConversationAggregate current = dashboardQueryRepository.aggregateConversations(condition, period);
        ConversationAggregate previous = dashboardQueryRepository.aggregateConversations(condition, prevPeriod);
        BigDecimal saving = dashboardQueryRepository.sumExpectedSavingCost(condition, period);
        BigDecimal prevSaving = dashboardQueryRepository.sumExpectedSavingCost(condition, prevPeriod);
        int hourlyLaborCost = settings.map(TeamSettings::getHourlyLaborCost)
                .orElse(TeamSettings.DEFAULT_HOURLY_LABOR_COST);

        CoverageResponse coverage = member.isLeader()
                ? CoverageResponse.of(
                        current.submittedMemberCount(),
                        teamMemberRepository.countByTeamId(teamId),
                        current.conversationCount(),
                        settings.map(TeamSettings::getTargetCount).orElse(null))
                : null;

        return KpiResponse.of(
                period,
                prevPeriod,
                MetricValue.of(current.tokensPerSuccess(), previous.tokensPerSuccess(), true),
                MetricValue.of(current.reworkRate(), previous.reworkRate(), false),
                MetricValue.of(current.successRate(), previous.successRate(), false),
                MetricValue.of(current.costPerSuccess(hourlyLaborCost), previous.costPerSuccess(hourlyLaborCost), true),
                MetricValue.of(saving, prevSaving, true),
                MetricValue.of(current.humanTimeSec(), previous.humanTimeSec(), false),
                MetricValue.of(current.conversationCount(), previous.conversationCount(), false),
                coverage);
    }

    /** 팀이 없으면 404 TEAM_NOT_FOUND, 팀 멤버가 아니면 403 NOT_A_MEMBER */
    private TeamMember getMember(Long teamId, Long userId) {
        if (!teamRepository.existsById(teamId)) {
            throw new CustomException(ErrorCode.TEAM_NOT_FOUND);
        }
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_A_MEMBER));
    }

    /**
     * 입력하지 않은 쪽은 팀 대상 기간으로 채운다.
     * 그래도 비어 있으면 400 TARGET_NOT_SET, from > to면 400 INVALID_REQUEST
     */
    private DatePeriod resolvePeriod(KpiRequest request, Optional<TeamSettings> settings) {
        LocalDate from = request.from() != null
                ? request.from()
                : settings.map(TeamSettings::getTargetFrom).orElse(null);
        LocalDate to = request.to() != null
                ? request.to()
                : settings.map(TeamSettings::getTargetTo).orElse(null);
        if (from == null || to == null) {
            throw new CustomException(ErrorCode.TARGET_NOT_SET);
        }
        return new DatePeriod(from, to);
    }
}
