package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.ConversationAggregate;
import com.babylion.team1.domain.dashboard.dto.ConversationDateRange;
import com.babylion.team1.domain.dashboard.dto.CostRow;
import com.babylion.team1.domain.dashboard.dto.CostType;
import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.KpiCondition;
import com.babylion.team1.domain.dashboard.dto.request.CostBreakdownRequest;
import com.babylion.team1.domain.dashboard.dto.request.CostGroupBy;
import com.babylion.team1.domain.dashboard.dto.request.DashboardScope;
import com.babylion.team1.domain.dashboard.dto.request.KpiRequest;
import com.babylion.team1.domain.dashboard.dto.request.OnboardingEffectRequest;
import com.babylion.team1.domain.dashboard.dto.request.TaskTypeStatsRequest;
import com.babylion.team1.domain.dashboard.dto.request.TrendMetric;
import com.babylion.team1.domain.dashboard.dto.request.TrendRequest;
import com.babylion.team1.domain.dashboard.dto.response.CoreMetrics;
import com.babylion.team1.domain.dashboard.dto.response.CostBreakdownResponse;
import com.babylion.team1.domain.dashboard.dto.response.CostItem;
import com.babylion.team1.domain.dashboard.dto.response.CostPoint;
import com.babylion.team1.domain.dashboard.dto.response.CoverageResponse;
import com.babylion.team1.domain.dashboard.dto.response.DayConversationItem;
import com.babylion.team1.domain.dashboard.dto.response.DayDetailResponse;
import com.babylion.team1.domain.dashboard.dto.response.DayTaskTypeItem;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
     * scope에 따라 본인 지표(ME) 또는 팀 전체 집계(TEAM)를 돌려준다. 생략하면 팀장은 TEAM, 팀원은 ME.
     * coverage는 팀 집계일 때만 채운다.
     * 팀이 없으면 404 TEAM_NOT_FOUND, 팀 멤버가 아니면 403 NOT_A_MEMBER, 팀원이 TEAM을 요청하면 403 LEADER_ONLY.
     */
    public KpiResponse getKpis(Long teamId, Long userId, KpiRequest request) {
        DashboardContext context = loadContext(teamId, userId, request.scope());
        DatePeriod period = context.period(request.from(), request.to());
        DatePeriod prevPeriod = period.previous();
        KpiCondition condition = context.condition(request.taskTypes(), request.models());

        ConversationAggregate current = dashboardQueryRepository.aggregateConversations(condition, period);
        ConversationAggregate previous = dashboardQueryRepository.aggregateConversations(condition, prevPeriod);
        BigDecimal saving = dashboardQueryRepository.sumExpectedSavingCost(condition, period);
        BigDecimal prevSaving = dashboardQueryRepository.sumExpectedSavingCost(condition, prevPeriod);
        int hourlyLaborCost = context.hourlyLaborCost();

        CoverageResponse coverage = context.isTeamScope()
                ? CoverageResponse.of(
                        current.submittedMemberCount(),
                        teamMemberRepository.countByTeamId(teamId),
                        current.conversationCount(),
                        context.targetCount())
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
     * 온보딩 시작일 전/후의 핵심 지표 3개를 비교한다. 집계 범위는 scope를 따른다(getKpis 참고).
     * 전: 대상 기간 시작일 ~ 온보딩 시작일 전날 / 후: 온보딩 시작일 ~ 대상 기간 종료일.
     * 온보딩 시작일이나 대상 기간이 없으면 before·after를 null로 돌려준다(화면에서 비교 카드 숨김).
     */
    public OnboardingEffectResponse getOnboardingEffect(Long teamId, Long userId, OnboardingEffectRequest request) {
        DashboardContext context = loadContext(teamId, userId, request.scope());
        LocalDate onboardingStartDate = context.onboardingStartDate();
        Optional<TeamSettings> targetSettings = context.settings().filter(TeamSettings::isTargetSet);
        if (onboardingStartDate == null || targetSettings.isEmpty()) {
            return OnboardingEffectResponse.notComparable(onboardingStartDate);
        }

        TeamSettings settings = targetSettings.get();
        KpiCondition condition = context.condition(request.taskTypes(), List.of());
        CoreMetrics before = aggregateCoreMetrics(condition, settings.getTargetFrom(), onboardingStartDate.minusDays(1));
        CoreMetrics after = aggregateCoreMetrics(condition, onboardingStartDate, settings.getTargetTo());
        return OnboardingEffectResponse.of(onboardingStartDate, before, after);
    }

    /**
     * 선택한 지표의 날짜별(DAY) 또는 주별(WEEK, 월요일 시작) 추이. 집계 범위는 scope를 따른다(getKpis 참고).
     * 대화가 없는 날(주)도 빠짐없이 채워 날짜 오름차순으로 돌려준다.
     * 표본 부족: 대화 수 < 하루 최소 대화 수 x 그 점에 포함된 일수(DAY면 1일, WEEK면 조회 기간 안에 든 일수).
     */
    public TrendResponse getTrends(Long teamId, Long userId, TrendRequest request) {
        TrendMetric metric = TrendMetric.from(request.metric());
        DashboardContext context = loadContext(teamId, userId, request.scope());
        DatePeriod period = context.period(request.from(), request.to());
        KpiCondition condition = context.condition(request.taskTypes(), request.models());
        Map<LocalDate, ConversationAggregate> daily = dashboardQueryRepository.aggregateConversationsByDate(condition, period);

        int minDailyConversations = context.minDailyConversations();
        int hourlyLaborCost = context.hourlyLaborCost();

        // 점(날짜 또는 주 시작일)을 먼저 모두 만들어 두고, 날짜별 집계를 해당 점에 더한다.
        Map<LocalDate, Integer> bucketDays = ChartBuckets.daysPerBucket(period, request.unit());
        Map<LocalDate, ConversationAggregate> buckets = new LinkedHashMap<>();
        bucketDays.keySet().forEach(bucket -> buckets.put(bucket, ConversationAggregate.empty()));
        daily.forEach((date, aggregate) ->
                buckets.merge(ChartBuckets.bucketOf(date, request.unit()), aggregate, ConversationAggregate::plus));

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

        return TrendResponse.of(metric, context.onboardingStartDate(), points);
    }

    /**
     * 기간별 비용 구조(누적 막대 차트용). 집계 범위는 scope를 따른다(getKpis 참고).
     * COST_TYPE은 호출비·검토비·수습비로 나누고, MODEL·TASK_TYPE은 대화별 총비용(호출+검토+수습)을 키별로 합산한다.
     * 비용은 반올림 전 정밀도로 합산한 뒤 점·키마다 원 단위로 반올림한다. 대화가 없는 날(주)도 채워 날짜 오름차순.
     */
    public CostBreakdownResponse getCostBreakdown(Long teamId, Long userId, CostBreakdownRequest request) {
        if (request.groupBy() == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "groupBy는 필수입니다.");
        }
        CostGroupBy groupBy = request.groupBy();
        DashboardContext context = loadContext(teamId, userId, request.scope());
        DatePeriod period = context.period(request.from(), request.to());
        KpiCondition condition = context.condition(request.taskTypes(), request.models());
        int hourlyLaborCost = context.hourlyLaborCost();

        // 점(날짜 또는 주 시작일)을 먼저 모두 만들어 두고, 집계 행을 해당 점의 키별로 더한다.
        Map<LocalDate, Map<String, BigDecimal>> buckets = new LinkedHashMap<>();
        ChartBuckets.daysPerBucket(period, request.unit())
                .keySet().forEach(bucket -> buckets.put(bucket, emptyCostItems(groupBy)));
        for (CostRow row : dashboardQueryRepository.aggregateCostsByDate(condition, period, groupBy)) {
            Map<String, BigDecimal> items = buckets.get(ChartBuckets.bucketOf(row.date(), request.unit()));
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
        // scope 생략과 같다: 팀장은 팀 전체(null), 팀원은 본인
        Long scopeUserId = resolveScopeUserId(getMember(teamId, userId), userId, null);

        ConversationDateRange dateRange = dashboardQueryRepository.findConversationDateRange(teamId, scopeUserId);
        return new FilterOptionsResponse(
                dashboardQueryRepository.findConversationTaskTypes(teamId, scopeUserId),
                dashboardQueryRepository.findConversationModels(teamId, scopeUserId),
                dateRange.first(),
                dateRange.last());
    }

    /**
     * 교육 필요 업무(팀장 전용). 업무 유형별 재작업률·성공 업무당 토큰·대화 수·상위 피드백 유형을
     * 재작업률 높은 순으로 돌려준다. 항상 팀 전체 집계이며 개인 식별 정보는 내려주지 않는다.
     * 팀원이면 403 LEADER_ONLY.
     */
    public TaskTypeStatsResponse getTaskTypeStats(Long teamId, Long userId, TaskTypeStatsRequest request) {
        // 항상 TEAM 범위로 집계하므로 팀원이면 여기서 403 LEADER_ONLY
        DashboardContext context = loadContext(teamId, userId, DashboardScope.TEAM);
        DatePeriod period = context.period(request.from(), request.to());
        KpiCondition condition = context.condition(List.of(), List.of());

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

    /**
     * 대시보드 조회의 공통 준비: 요청자 검증 → 집계 범위 결정 → 팀 설정 조회.
     * 팀이 없으면 404 TEAM_NOT_FOUND, 팀 멤버가 아니면 403 NOT_A_MEMBER, 팀원이 TEAM을 요청하면 403 LEADER_ONLY.
     */
    private DashboardContext loadContext(Long teamId, Long userId, DashboardScope scope) {
        Long scopeUserId = resolveScopeUserId(getMember(teamId, userId), userId, scope);
        return new DashboardContext(teamId, scopeUserId, teamSettingsRepository.findByTeamId(teamId));
    }

    /**
     * 집계 대상 userId. 팀 전체 집계면 null, 본인 집계면 요청자 userId.
     * scope를 생략하면 팀장은 TEAM, 팀원은 ME로 본다. 팀원이 TEAM을 요청하면 403 LEADER_ONLY.
     */
    private Long resolveScopeUserId(TeamMember member, Long userId, DashboardScope scope) {
        DashboardScope resolved = scope != null
                ? scope
                : (member.isLeader() ? DashboardScope.TEAM : DashboardScope.ME);
        if (resolved == DashboardScope.ME) {
            return userId;
        }
        if (!member.isLeader()) {
            throw new CustomException(ErrorCode.LEADER_ONLY);
        }
        return null;
    }

    /** 팀이 없으면 404 TEAM_NOT_FOUND, 팀 멤버가 아니면 403 NOT_A_MEMBER */
    private TeamMember getMember(Long teamId, Long userId) {
        if (!teamRepository.existsById(teamId)) {
            throw new CustomException(ErrorCode.TEAM_NOT_FOUND);
        }
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_A_MEMBER));
    }
}
