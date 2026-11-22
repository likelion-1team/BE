package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.ConversationAggregate;
import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.KpiCondition;
import com.babylion.team1.domain.dashboard.dto.request.KpiRequest;
import com.babylion.team1.domain.dashboard.dto.request.KpiUnit;
import com.babylion.team1.domain.dashboard.dto.request.OnboardingEffectRequest;
import com.babylion.team1.domain.dashboard.dto.request.TrendMetric;
import com.babylion.team1.domain.dashboard.dto.request.TrendRequest;
import com.babylion.team1.domain.dashboard.dto.response.CoreMetrics;
import com.babylion.team1.domain.dashboard.dto.response.CoverageResponse;
import com.babylion.team1.domain.dashboard.dto.response.FilterOptionsResponse;
import com.babylion.team1.domain.dashboard.dto.response.KpiResponse;
import com.babylion.team1.domain.dashboard.dto.response.MetricValue;
import com.babylion.team1.domain.dashboard.dto.response.OnboardingEffectResponse;
import com.babylion.team1.domain.dashboard.dto.response.TrendPoint;
import com.babylion.team1.domain.dashboard.dto.response.TrendResponse;
import com.babylion.team1.domain.dashboard.repository.DashboardQueryRepository;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        DatePeriod period = resolvePeriod(request.from(), request.to(), settings);
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

    /**
     * 온보딩 시작일 전/후의 핵심 지표 3개를 비교한다. 팀원이면 본인 값, 팀장이면 팀 집계.
     * 전: 대상 기간 시작일 ~ 온보딩 시작일 전날 / 후: 온보딩 시작일 ~ 대상 기간 종료일.
     * 온보딩 시작일이나 대상 기간이 없으면 before·after를 null로 돌려준다(화면에서 비교 카드 숨김).
     */
    public OnboardingEffectResponse getOnboardingEffect(Long teamId, Long userId, OnboardingEffectRequest request) {
        TeamMember member = getMember(teamId, userId);
        Optional<TeamSettings> settings = teamSettingsRepository.findByTeamId(teamId);
        LocalDate onboardingStartDate = settings.map(TeamSettings::getOnboardingStartDate).orElse(null);
        if (onboardingStartDate == null || !settings.get().isTargetSet()) {
            return OnboardingEffectResponse.notComparable(onboardingStartDate);
        }

        TeamSettings teamSettings = settings.get();
        KpiCondition condition = new KpiCondition(
                teamId,
                member.isLeader() ? null : userId,
                request.taskTypes(),
                List.of());
        CoreMetrics before = aggregateCoreMetrics(condition, teamSettings.getTargetFrom(), onboardingStartDate.minusDays(1));
        CoreMetrics after = aggregateCoreMetrics(condition, onboardingStartDate, teamSettings.getTargetTo());
        return OnboardingEffectResponse.of(onboardingStartDate, before, after);
    }

    /**
     * 선택한 지표의 날짜별(DAY) 또는 주별(WEEK, 월요일 시작) 추이. 팀원이면 본인 값, 팀장이면 팀 집계.
     * 대화가 없는 날(주)도 빠짐없이 채워 날짜 오름차순으로 돌려준다.
     * 표본 부족: 대화 수 < 하루 최소 대화 수 x 그 점에 포함된 일수(DAY면 1일, WEEK면 조회 기간 안에 든 일수).
     */
    public TrendResponse getTrends(Long teamId, Long userId, TrendRequest request) {
        TrendMetric metric = TrendMetric.from(request.metric());
        TeamMember member = getMember(teamId, userId);
        Optional<TeamSettings> settings = teamSettingsRepository.findByTeamId(teamId);

        DatePeriod period = resolvePeriod(request.from(), request.to(), settings);
        KpiCondition condition = new KpiCondition(
                teamId,
                member.isLeader() ? null : userId,
                request.taskTypes(),
                request.models());
        Map<LocalDate, ConversationAggregate> daily = dashboardQueryRepository.aggregateConversationsByDate(condition, period);

        int minDailyConversations = settings.map(TeamSettings::getMinDailyConversations)
                .orElse(TeamSettings.DEFAULT_MIN_DAILY_CONVERSATIONS);
        int hourlyLaborCost = settings.map(TeamSettings::getHourlyLaborCost)
                .orElse(TeamSettings.DEFAULT_HOURLY_LABOR_COST);

        // 점(날짜 또는 주 시작일)마다 집계와 포함 일수를 모은다. LinkedHashMap이라 날짜 오름차순이 유지된다.
        Map<LocalDate, ConversationAggregate> buckets = new LinkedHashMap<>();
        Map<LocalDate, Integer> bucketDays = new LinkedHashMap<>();
        for (LocalDate date = period.from(); !date.isAfter(period.to()); date = date.plusDays(1)) {
            LocalDate bucket = request.unit() == KpiUnit.WEEK
                    ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    : date;
            ConversationAggregate dayAggregate = daily.getOrDefault(date, ConversationAggregate.empty());
            buckets.merge(bucket, dayAggregate, ConversationAggregate::plus);
            bucketDays.merge(bucket, 1, Integer::sum);
        }

        List<TrendPoint> points = buckets.entrySet().stream()
                .map(entry -> {
                    ConversationAggregate aggregate = entry.getValue();
                    long threshold = (long) minDailyConversations * bucketDays.get(entry.getKey());
                    return new TrendPoint(
                            entry.getKey(),
                            metric.calculate(aggregate, hourlyLaborCost),
                            aggregate.conversationCount(),
                            aggregate.conversationCount() < threshold);
                })
                .toList();

        return TrendResponse.of(metric, settings.map(TeamSettings::getOnboardingStartDate).orElse(null), points);
    }

    /** 온보딩 시작일이 대상 기간 밖이라 한쪽 기간이 비면(from > to) 대화 0건으로 본다 */
    private CoreMetrics aggregateCoreMetrics(KpiCondition condition, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return CoreMetrics.from(ConversationAggregate.empty());
        }
        return CoreMetrics.from(dashboardQueryRepository.aggregateConversations(condition, new DatePeriod(from, to)));
    }

    /**
     * 대시보드 필터 옵션. 팀원이면 본인 대화, 팀장이면 팀 전체 대화 기준.
     * 대상 기간과 상관없이 저장된 대화 전체에서 뽑는다.
     */
    public FilterOptionsResponse getFilterOptions(Long teamId, Long userId) {
        TeamMember member = getMember(teamId, userId);
        Long scopeUserId = member.isLeader() ? null : userId;

        LocalDate[] dateRange = dashboardQueryRepository.findConversationDateRange(teamId, scopeUserId);
        return new FilterOptionsResponse(
                dashboardQueryRepository.findConversationTaskTypes(teamId, scopeUserId),
                dashboardQueryRepository.findConversationModels(teamId, scopeUserId),
                dateRange[0],
                dateRange[1]);
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
    private DatePeriod resolvePeriod(LocalDate requestFrom, LocalDate requestTo, Optional<TeamSettings> settings) {
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
}
