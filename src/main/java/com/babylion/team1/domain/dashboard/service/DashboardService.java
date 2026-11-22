package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.ConversationAggregate;
import com.babylion.team1.domain.dashboard.dto.CostRow;
import com.babylion.team1.domain.dashboard.dto.CostType;
import com.babylion.team1.domain.dashboard.dto.request.CostBreakdownRequest;
import com.babylion.team1.domain.dashboard.dto.request.CostGroupBy;
import com.babylion.team1.domain.dashboard.dto.response.CostBreakdownResponse;
import com.babylion.team1.domain.dashboard.dto.response.CostItem;
import com.babylion.team1.domain.dashboard.dto.response.CostPoint;
import com.babylion.team1.domain.dashboard.dto.response.DayConversationItem;
import com.babylion.team1.domain.dashboard.dto.response.DayDetailResponse;
import com.babylion.team1.domain.dashboard.dto.response.DayTaskTypeItem;
import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.KpiCondition;
import com.babylion.team1.domain.dashboard.dto.request.KpiRequest;
import com.babylion.team1.domain.dashboard.dto.request.KpiUnit;
import com.babylion.team1.domain.dashboard.dto.request.OnboardingEffectRequest;
import com.babylion.team1.domain.dashboard.dto.request.TaskTypeStatsRequest;
import com.babylion.team1.domain.dashboard.dto.request.TrendMetric;
import com.babylion.team1.domain.dashboard.dto.request.TrendRequest;
import com.babylion.team1.domain.dashboard.dto.response.CoreMetrics;
import com.babylion.team1.domain.dashboard.dto.response.CoverageResponse;
import com.babylion.team1.domain.dashboard.dto.response.FilterOptionsResponse;
import com.babylion.team1.domain.dashboard.dto.response.KpiResponse;
import com.babylion.team1.domain.dashboard.dto.response.MetricValue;
import com.babylion.team1.domain.dashboard.dto.response.OnboardingEffectResponse;
import com.babylion.team1.domain.dashboard.dto.response.TaskTypeOption;
import com.babylion.team1.domain.dashboard.dto.response.TaskTypeStatsItem;
import com.babylion.team1.domain.dashboard.dto.response.TaskTypeStatsResponse;
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
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /** 교육 필요 업무에서 업무 유형마다 보여줄 피드백 유형 수 */
    private static final int TOP_FEEDBACK_TYPE_LIMIT = 3;
    /** 비용 구조에서 model이 NULL인 대화의 키 */
    private static final String UNKNOWN_COST_KEY = "UNKNOWN";

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
            LocalDate bucket = bucketOf(date, request.unit());
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

    /**
     * 기간별 비용 구조(누적 막대 차트용). 팀원이면 본인 값, 팀장이면 팀 집계.
     * COST_TYPE은 호출비·검토비·수습비로 나누고, MODEL·TASK_TYPE은 대화별 총비용(호출+검토+수습)을 키별로 합산한다.
     * 비용은 반올림 전 정밀도로 합산한 뒤 점·키마다 원 단위로 반올림한다. 대화가 없는 날(주)도 채워 날짜 오름차순.
     */
    public CostBreakdownResponse getCostBreakdown(Long teamId, Long userId, CostBreakdownRequest request) {
        if (request.groupBy() == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "groupBy는 필수입니다.");
        }
        CostGroupBy groupBy = request.groupBy();
        TeamMember member = getMember(teamId, userId);
        Optional<TeamSettings> settings = teamSettingsRepository.findByTeamId(teamId);

        DatePeriod period = resolvePeriod(request.from(), request.to(), settings);
        KpiCondition condition = new KpiCondition(
                teamId,
                member.isLeader() ? null : userId,
                request.taskTypes(),
                request.models());
        int hourlyLaborCost = settings.map(TeamSettings::getHourlyLaborCost)
                .orElse(TeamSettings.DEFAULT_HOURLY_LABOR_COST);

        // 점(날짜 또는 주 시작일)을 먼저 모두 만들어 두고, 집계 행을 해당 점의 키별로 더한다.
        Map<LocalDate, Map<String, BigDecimal>> buckets = new LinkedHashMap<>();
        for (LocalDate date = period.from(); !date.isAfter(period.to()); date = date.plusDays(1)) {
            buckets.computeIfAbsent(bucketOf(date, request.unit()), bucket -> emptyCostItems(groupBy));
        }
        for (CostRow row : dashboardQueryRepository.aggregateCostsByDate(condition, period, groupBy)) {
            Map<String, BigDecimal> items = buckets.get(bucketOf(row.date(), request.unit()));
            if (groupBy == CostGroupBy.COST_TYPE) {
                items.merge(CostType.CALL.name(), row.callCost(), BigDecimal::add);
                items.merge(CostType.REVIEW.name(), row.reviewCost(hourlyLaborCost), BigDecimal::add);
                items.merge(CostType.REWORK.name(), row.reworkCost(hourlyLaborCost), BigDecimal::add);
            } else {
                String key = row.key() != null ? row.key() : UNKNOWN_COST_KEY;
                items.merge(key, row.totalCost(hourlyLaborCost), BigDecimal::add);
            }
        }

        List<CostPoint> points = buckets.entrySet().stream()
                .map(entry -> new CostPoint(
                        entry.getKey(),
                        entry.getValue().entrySet().stream()
                                .map(item -> CostItem.of(item.getKey(), item.getValue()))
                                .toList()))
                .toList();
        return CostBreakdownResponse.of(groupBy, points);
    }

    /**
     * 한 점의 비어 있는 비용 항목.
     * COST_TYPE은 CALL·REVIEW·REWORK를 0으로 미리 채운다(순서 고정).
     * MODEL·TASK_TYPE은 데이터가 있는 키만 이름순으로 담고, UNKNOWN은 맨 뒤에 둔다.
     */
    private Map<String, BigDecimal> emptyCostItems(CostGroupBy groupBy) {
        if (groupBy == CostGroupBy.COST_TYPE) {
            Map<String, BigDecimal> items = new LinkedHashMap<>();
            for (CostType costType : CostType.values()) {
                items.put(costType.name(), BigDecimal.ZERO);
            }
            return items;
        }
        return new TreeMap<>(Comparator.comparing((String key) -> key.equals(UNKNOWN_COST_KEY))
                .thenComparing(Comparator.naturalOrder()));
    }

    /** DAY면 그날, WEEK면 그 주의 월요일 */
    private LocalDate bucketOf(LocalDate date, KpiUnit unit) {
        return unit == KpiUnit.WEEK ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : date;
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

    /**
     * 교육 필요 업무(팀장 전용). 업무 유형별 재작업률·성공 업무당 토큰·대화 수·상위 피드백 유형을
     * 재작업률 높은 순으로 돌려준다. 항상 팀 전체 집계이며 개인 식별 정보는 내려주지 않는다.
     * 팀원이면 403 LEADER_ONLY.
     */
    public TaskTypeStatsResponse getTaskTypeStats(Long teamId, Long userId, TaskTypeStatsRequest request) {
        TeamMember member = getMember(teamId, userId);
        if (!member.isLeader()) {
            throw new CustomException(ErrorCode.LEADER_ONLY);
        }
        Optional<TeamSettings> settings = teamSettingsRepository.findByTeamId(teamId);
        DatePeriod period = resolvePeriod(request.from(), request.to(), settings);
        KpiCondition condition = new KpiCondition(teamId, null, List.of(), List.of());

        Map<TaskTypeOption, ConversationAggregate> byTaskType =
                dashboardQueryRepository.aggregateConversationsByTaskType(condition, period);
        Map<Long, List<String>> topFeedbackTypes =
                dashboardQueryRepository.findTopFeedbackTypes(condition, period, TOP_FEEDBACK_TYPE_LIMIT);

        List<TaskTypeStatsItem> items = byTaskType.entrySet().stream()
                .map(entry -> TaskTypeStatsItem.of(
                        entry.getKey(),
                        entry.getValue(),
                        topFeedbackTypes.getOrDefault(entry.getKey().id(), List.of())))
                .sorted(TaskTypeStatsItem.REWORK_RATE_DESC)
                .toList();
        return new TaskTypeStatsResponse(items);
    }

    /**
     * 차트에서 클릭한 날짜의 상세.
     * 팀원: 그날 본인 대화 목록. 팀장: 그날 팀 업무 유형별 건수·지표 + 팀장 본인 대화 목록.
     * 다른 팀원의 대화 제목·user_id 등 개인 식별 정보는 팀장에게도 내려주지 않는다.
     */
    public DayDetailResponse getDayDetail(Long teamId, Long userId, String dateValue) {
        LocalDate date = parseDate(dateValue);
        TeamMember member = getMember(teamId, userId);
        // 대화 목록은 역할과 상관없이 항상 요청자 본인 것만 담는다
        List<DayConversationItem> ownConversations = dashboardQueryRepository.findDayConversations(teamId, userId, date);

        if (!member.isLeader()) {
            return DayDetailResponse.forMember(date, ownConversations);
        }
        KpiCondition condition = new KpiCondition(teamId, null, List.of(), List.of());
        List<DayTaskTypeItem> taskTypes = dashboardQueryRepository
                .aggregateConversationsByTaskType(condition, new DatePeriod(date, date))
                .entrySet().stream()
                .map(entry -> DayTaskTypeItem.of(entry.getKey().name(), entry.getValue()))
                .toList();
        return DayDetailResponse.forLeader(date, ownConversations, taskTypes);
    }

    /**
     * yyyy-MM-dd 문자열을 날짜로 바꾼다. 형식이 잘못되면 400 INVALID_REQUEST.
     * LocalDate로 바로 받으면 전역 처리기가 400 INVALID_TYPE으로 응답하는데, 이 동작은 다른 API도 쓰고 있어 바꾸지 않는다.
     */
    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "날짜는 yyyy-MM-dd 형식이어야 합니다.");
        }
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
