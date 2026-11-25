package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.CohortItem;
import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.MetricCode;
import com.babylion.team1.domain.dashboard.dto.MetricStatus;
import com.babylion.team1.domain.dashboard.dto.request.DashboardScope;
import com.babylion.team1.domain.dashboard.dto.request.FilterOptionsRequest;
import com.babylion.team1.domain.dashboard.dto.request.KpiRequest;
import com.babylion.team1.domain.dashboard.dto.request.KpiUnit;
import com.babylion.team1.domain.dashboard.dto.request.TrendRequest;
import com.babylion.team1.domain.dashboard.dto.response.FilterOptionsResponse;
import com.babylion.team1.domain.dashboard.dto.response.KpiResponse;
import com.babylion.team1.domain.dashboard.dto.response.MetricResponse;
import com.babylion.team1.domain.dashboard.dto.response.TrendPoint;
import com.babylion.team1.domain.dashboard.dto.response.TrendResponse;
import com.babylion.team1.domain.dashboard.repository.DashboardCohortRepository;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamGoalRepository;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.team.repository.TeamTaskTypeRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.policy.ServerPolicy;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 v4 API(필터 옵션, KPI 카드, 추이). 분석 항목(analysis_items)과 직원이 확인한 값으로 계산한다.
 * 팀장과 팀원 모두 ME와 TEAM을 조회할 수 있고, 같은 조건이면 팀 지표도 같다.
 * 공통 에러: 다른 팀이거나 없는 팀이면 404 RESOURCE_NOT_FOUND, 값이 잘못되면 422 VALIDATION_FAILED.
 * 표본 부족이나 숨김은 에러가 아니라 지표의 status로 알린다.
 * parameterNames는 요청에 실제로 들어온 쿼리 파라미터 이름이며, 지원하지 않는 파라미터를 거부하는 데 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardMetricService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TeamMemberRepository teamMemberRepository;
    private final TeamSettingsRepository teamSettingsRepository;
    private final TeamTaskTypeRepository teamTaskTypeRepository;
    private final TeamGoalRepository teamGoalRepository;
    private final DashboardCohortRepository dashboardCohortRepository;

    public FilterOptionsResponse getFilterOptions(Long teamId, Long userId, FilterOptionsRequest request,
            Set<String> parameterNames) {
        DashboardScope scope = authorize(teamId, userId, request.scope());
        DashboardParameters.requireSupported(parameterNames, Set.of("scope"));

        List<TeamSettings> versions = teamSettingsRepository.findSavedVersions(teamId);
        return FilterOptionsResponse.of(
                scope,
                teamTaskTypeRepository.findByTeamIdAndIsActiveTrueOrderById(teamId).stream()
                        .map(FilterOptionsResponse.TaskType::from)
                        .toList(),
                versions.stream().map(TeamSettings::getVersion).toList(),
                scope == DashboardScope.TEAM ? findCompletedWeeks(versions) : List.of());
    }

    public KpiResponse getKpis(Long teamId, Long userId, KpiRequest request, Set<String> parameterNames) {
        DashboardScope scope = authorize(teamId, userId, request.scope());
        DashboardParameters.requireSupported(parameterNames, scope == DashboardScope.ME
                ? Set.of("scope", "from", "to", "settingsVersion")
                : Set.of("scope", "weekStart", "settingsVersion"));
        DatePeriod period = period(scope, request.from(), request.to(), request.weekStart());
        CohortQuery query = toQuery(teamId, userId, scope, period, request.settingsVersion());

        Cohort cohort = findCohort(query);
        List<MetricResponse> metrics = MetricCalculator.calculate(scope, cohort.inScopeItems());
        boolean suppressed = metrics.stream().anyMatch(metric -> metric.status() == MetricStatus.SUPPRESSED);
        return new KpiResponse(
                scope,
                query.cohortId(),
                now(),
                ServerPolicy.METRIC_DEFINITION_VERSION,
                ServerPolicy.VERSION,
                query.settingsVersion(),
                teamGoalRepository.findByTeamId(teamId)
                        .map(KpiResponse.Goals::from)
                        .orElseGet(KpiResponse.Goals::unset),
                suppressed ? null : cohort.coverage(),
                metrics);
    }

    /** 대화가 없는 구간도 빠짐없이 담고, 상태는 구간마다 판정한다. WEEK의 양 끝 주는 조회 기간 안의 대화만 센다. */
    public TrendResponse getTrends(Long teamId, Long userId, TrendRequest request, Set<String> parameterNames) {
        DashboardScope scope = authorize(teamId, userId, request.scope());
        DashboardParameters.requireSupported(parameterNames, scope == DashboardScope.ME
                ? Set.of("scope", "from", "to", "settingsVersion", "metric", "unit")
                : Set.of("scope", "weekStart", "settingsVersion", "metric", "unit"));
        MetricCode code = DashboardParameters.metric(request.metric());
        KpiUnit unit = DashboardParameters.unit(request.unit(), scope);
        DatePeriod period = period(scope, request.from(), request.to(), request.weekStart());
        DashboardParameters.requireTrendPeriod(period);
        CohortQuery query = toQuery(teamId, userId, scope, period, request.settingsVersion());

        Map<LocalDate, List<CohortItem>> itemsByBucket = findCohort(query).inScopeItems().stream()
                .collect(Collectors.groupingBy(item -> ChartBuckets.bucketOf(item.conversationDate(), unit)));
        List<TrendPoint> points = ChartBuckets.bucketsIn(period, unit).stream()
                .map(bucket -> new TrendPoint(bucket,
                        calculate(scope, code, itemsByBucket.getOrDefault(bucket, List.of()))))
                .toList();
        return new TrendResponse(scope, query.cohortId(), now(), ServerPolicy.METRIC_DEFINITION_VERSION, points);
    }

    /** 다른 지표를 역산할 수 있어 숨겨야 하는지도 판정해야 하므로, 지표 하나만 필요해도 전체를 계산해서 고른다 */
    private MetricResponse calculate(DashboardScope scope, MetricCode code, List<CohortItem> items) {
        return MetricCalculator.calculate(scope, items).stream()
                .filter(metric -> metric.code() == code)
                .findFirst()
                .orElseThrow();
    }

    /** 팀 소속 → scope 값 순서로 확인한다. 다른 팀 사용자에게는 팀의 존재 여부를 알리지 않는다. */
    private DashboardScope authorize(Long teamId, Long userId, String scopeValue) {
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        return DashboardParameters.scope(scopeValue);
    }

    private DatePeriod period(DashboardScope scope, String from, String to, String weekStart) {
        return scope == DashboardScope.ME
                ? DashboardParameters.personalPeriod(from, to)
                : DashboardParameters.completedWeek(weekStart, LocalDate.now(KST));
    }

    private CohortQuery toQuery(Long teamId, Long userId, DashboardScope scope, DatePeriod period,
            String settingsVersionValue) {
        int settingsVersion = DashboardParameters.settingsVersion(settingsVersionValue);
        TeamSettings settings = teamSettingsRepository.findSavedVersions(teamId).stream()
                .filter(saved -> saved.getVersion() == settingsVersion)
                .findFirst()
                .orElseThrow(() -> DashboardParameters.invalid("settingsVersion", "존재하지 않는 기준 버전입니다."));
        return new CohortQuery(teamId, scope, scope == DashboardScope.ME ? userId : null, period,
                settings.getId(), settingsVersion);
    }

    private Cohort findCohort(CohortQuery query) {
        return new Cohort(dashboardCohortRepository.findItems(query.teamId(), query.userId(), query.settingsId(),
                query.period()));
    }

    /**
     * 저장된 기준 버전들의 대상 기간에 걸친 완료된 주(월요일 오름차순). 이번 주는 포함하지 않는다.
     * 대화가 있는 주만 고르면 팀원의 제출 시점이 드러나므로 대상 기간으로만 정한다.
     */
    private List<FilterOptionsResponse.Week> findCompletedWeeks(List<TeamSettings> versions) {
        LocalDate thisMonday = ChartBuckets.bucketOf(LocalDate.now(KST), KpiUnit.WEEK);
        return versions.stream()
                .filter(TeamSettings::isTargetSet)
                .flatMap(settings -> Stream.iterate(
                        ChartBuckets.bucketOf(settings.getTargetFrom(), KpiUnit.WEEK),
                        monday -> !monday.isAfter(settings.getTargetTo()) && monday.isBefore(thisMonday),
                        monday -> monday.plusWeeks(1)))
                .distinct()
                .sorted()
                .map(FilterOptionsResponse.Week::startingAt)
                .toList();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS);
    }
}
