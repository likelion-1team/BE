package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.CohortItem;
import com.babylion.team1.domain.dashboard.dto.MetricCode;
import com.babylion.team1.domain.dashboard.dto.MetricStatus;
import com.babylion.team1.domain.dashboard.dto.request.DashboardScope;
import com.babylion.team1.domain.dashboard.dto.response.MetricResponse;
import com.babylion.team1.global.policy.ServerPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** 지표 5개의 값과 상태를 계산한다. KPI 카드와 추이가 이 계산을 함께 쓴다. */
final class MetricCalculator {

    private static final int RATIO_SCALE = 4;
    private static final int TOKEN_SCALE = 2;

    private MetricCalculator() {
    }

    /** items는 집계 대상(IN_SCOPE) 항목이어야 한다. MetricCode 선언 순서대로 돌려준다. */
    static List<MetricResponse> calculate(DashboardScope scope, List<CohortItem> items) {
        Set<MetricCode> suppressed = scope == DashboardScope.TEAM
                ? findSuppressed(items)
                : EnumSet.noneOf(MetricCode.class);
        return Arrays.stream(MetricCode.values())
                .map(code -> suppressed.contains(code)
                        ? MetricResponse.hidden(code, MetricStatus.SUPPRESSED)
                        : calculate(scope, code, items))
                .toList();
    }

    private static MetricResponse calculate(DashboardScope scope, MetricCode code, List<CohortItem> items) {
        Counts counts = count(code, items);
        MetricStatus status = judge(code, counts, items);
        // v4 명세 7.2: 표본 부족일 때의 표본 수치는 ME에만 노출한다
        if (status == MetricStatus.INSUFFICIENT_SAMPLE && scope == DashboardScope.TEAM) {
            return MetricResponse.hidden(code, status);
        }
        BigDecimal value = status == MetricStatus.AVAILABLE ? divide(code, counts) : null;
        return MetricResponse.of(code, value, counts.numerator(), counts.denominator(), counts.sampleSize(),
                counts.missingCount(), status);
    }

    /** 토큰을 하나라도 측정하지 못하면 전체 토큰 합을 알 수 없어 분자를 null로 둔다 */
    private static Counts count(MetricCode code, List<CohortItem> items) {
        long total = items.size();
        long success = count(items, CohortItem::isSuccessConfirmed);
        long outcomeConfirmed = count(items, CohortItem::isOutcomeConfirmed);
        long reworkConfirmed = count(items, CohortItem::isReworkConfirmed);
        return switch (code) {
            case CONFIRMED_SUCCESS_RATE ->
                    new Counts(success, outcomeConfirmed, outcomeConfirmed, total - outcomeConfirmed);
            case OUTCOME_CONFIRMATION_RATE ->
                    new Counts(outcomeConfirmed, total, total, total - outcomeConfirmed);
            case ERROR_REWORK_RATE ->
                    new Counts(count(items, CohortItem::isErrorRework), reworkConfirmed, reworkConfirmed,
                            total - reworkConfirmed);
            case REWORK_CONFIRMATION_RATE ->
                    new Counts(reworkConfirmed, total, total, total - reworkConfirmed);
            case TOKENS_PER_CONFIRMED_SUCCESS -> {
                long measured = count(items, CohortItem::isTokenMeasured);
                Long tokens = measured == total
                        ? items.stream().mapToLong(CohortItem::estimatedTokens).sum()
                        : null;
                yield new Counts(tokens, success, measured, total - measured);
            }
        };
    }

    /** MetricStatus 선언 순서대로 판정한다. SUPPRESSED는 먼저 걸러낸 뒤에 호출한다. */
    private static MetricStatus judge(MetricCode code, Counts counts, List<CohortItem> items) {
        boolean tokenMetric = code == MetricCode.TOKENS_PER_CONFIRMED_SUCCESS;
        // 조회 단계에서 기준 버전 하나로 거르므로 정상 요청에서는 섞이지 않는다.
        // 토큰은 출처를 따로 저장하지 않고 서버 토크나이저 추정값 하나만 쓰므로 섞일 수 없다.
        if (items.stream().mapToInt(CohortItem::settingsVersion).distinct().count() > 1) {
            return MetricStatus.NOT_COMPARABLE;
        }
        if (items.isEmpty()) {
            return MetricStatus.NO_DATA;
        }
        if (tokenMetric && counts.missingCount() > 0) {
            return MetricStatus.NOT_MEASURED;
        }
        if (counts.denominator() == 0) {
            return MetricStatus.ZERO_DENOMINATOR;
        }
        long sample = tokenMetric ? items.size() : counts.denominator();
        if (sample < ServerPolicy.MIN_SAMPLE_SIZE) {
            return MetricStatus.INSUFFICIENT_SAMPLE;
        }
        if (tokenMetric && !hasEnoughConfirmation(items)) {
            return MetricStatus.INSUFFICIENT_CONFIRMATION;
        }
        return MetricStatus.AVAILABLE;
    }

    /** 반올림 오차가 없도록 나누지 않고 비교한다 */
    private static boolean hasEnoughConfirmation(List<CohortItem> items) {
        BigDecimal required = ServerPolicy.MIN_OUTCOME_CONFIRMATION_RATE.multiply(BigDecimal.valueOf(items.size()));
        return BigDecimal.valueOf(count(items, CohortItem::isOutcomeConfirmed)).compareTo(required) >= 0;
    }

    /** 유효 기여자가 부족한 지표와, 그 지표의 건수를 역산할 수 있는 지표 */
    private static Set<MetricCode> findSuppressed(List<CohortItem> items) {
        Set<MetricCode> suppressed = EnumSet.noneOf(MetricCode.class);
        for (MetricCode code : MetricCode.values()) {
            if (countContributors(code, items) < ServerPolicy.MIN_TEAM_CONTRIBUTORS) {
                suppressed.add(code);
            }
        }
        // 결과 확인률의 분자 = 성공률의 분모, 토큰 지표의 분모 = 성공률의 분자
        if (suppressed.contains(MetricCode.CONFIRMED_SUCCESS_RATE)) {
            suppressed.add(MetricCode.OUTCOME_CONFIRMATION_RATE);
            suppressed.add(MetricCode.TOKENS_PER_CONFIRMED_SUCCESS);
        }
        // 재작업 확인률의 분자 = 오류 재작업률의 분모
        if (suppressed.contains(MetricCode.ERROR_REWORK_RATE)) {
            suppressed.add(MetricCode.REWORK_CONFIRMATION_RATE);
        }
        return suppressed;
    }

    /** 지표의 sampleSize에 들어가는 대화를 하나라도 가진 사람 수 */
    private static long countContributors(MetricCode code, List<CohortItem> items) {
        Predicate<CohortItem> valid = switch (code) {
            case CONFIRMED_SUCCESS_RATE -> CohortItem::isOutcomeConfirmed;
            case ERROR_REWORK_RATE -> CohortItem::isReworkConfirmed;
            case OUTCOME_CONFIRMATION_RATE, REWORK_CONFIRMATION_RATE -> item -> true;
            case TOKENS_PER_CONFIRMED_SUCCESS -> CohortItem::isTokenMeasured;
        };
        return items.stream().filter(valid).map(CohortItem::userId).distinct().count();
    }

    private static BigDecimal divide(MetricCode code, Counts counts) {
        int scale = code.getUnit() == MetricCode.Unit.RATIO ? RATIO_SCALE : TOKEN_SCALE;
        return BigDecimal.valueOf(counts.numerator())
                .divide(BigDecimal.valueOf(counts.denominator()), scale, RoundingMode.HALF_UP);
    }

    private static long count(List<CohortItem> items, Predicate<CohortItem> predicate) {
        return items.stream().filter(predicate).count();
    }

    private record Counts(Long numerator, long denominator, long sampleSize, long missingCount) {
    }
}
