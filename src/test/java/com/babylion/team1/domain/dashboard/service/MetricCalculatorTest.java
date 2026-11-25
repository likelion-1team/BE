package com.babylion.team1.domain.dashboard.service;

import static com.babylion.team1.domain.analysis.entity.AnalysisOutcome.FAILURE;
import static com.babylion.team1.domain.analysis.entity.AnalysisOutcome.SUCCESS;
import static com.babylion.team1.domain.analysis.entity.AnalysisOutcome.UNKNOWN;
import static com.babylion.team1.domain.analysis.entity.AnalysisReworkType.ERROR_CORRECTION;
import static com.babylion.team1.domain.analysis.entity.AnalysisReworkType.NONE;
import static com.babylion.team1.domain.dashboard.dto.MetricCode.CONFIRMED_SUCCESS_RATE;
import static com.babylion.team1.domain.dashboard.dto.MetricCode.ERROR_REWORK_RATE;
import static com.babylion.team1.domain.dashboard.dto.MetricCode.OUTCOME_CONFIRMATION_RATE;
import static com.babylion.team1.domain.dashboard.dto.MetricCode.REWORK_CONFIRMATION_RATE;
import static com.babylion.team1.domain.dashboard.dto.MetricCode.TOKENS_PER_CONFIRMED_SUCCESS;
import static com.babylion.team1.domain.dashboard.dto.MetricStatus.AVAILABLE;
import static com.babylion.team1.domain.dashboard.dto.MetricStatus.INSUFFICIENT_CONFIRMATION;
import static com.babylion.team1.domain.dashboard.dto.MetricStatus.INSUFFICIENT_SAMPLE;
import static com.babylion.team1.domain.dashboard.dto.MetricStatus.NOT_COMPARABLE;
import static com.babylion.team1.domain.dashboard.dto.MetricStatus.NOT_MEASURED;
import static com.babylion.team1.domain.dashboard.dto.MetricStatus.NO_DATA;
import static com.babylion.team1.domain.dashboard.dto.MetricStatus.SUPPRESSED;
import static com.babylion.team1.domain.dashboard.dto.MetricStatus.ZERO_DENOMINATOR;
import static org.assertj.core.api.Assertions.assertThat;

import com.babylion.team1.domain.analysis.entity.AnalysisItemStatus;
import com.babylion.team1.domain.analysis.entity.AnalysisOutcome;
import com.babylion.team1.domain.analysis.entity.AnalysisReworkType;
import com.babylion.team1.domain.analysis.entity.ScopeClassification;
import com.babylion.team1.domain.dashboard.dto.CohortItem;
import com.babylion.team1.domain.dashboard.dto.MetricCode;
import com.babylion.team1.domain.dashboard.dto.MetricStatus;
import com.babylion.team1.domain.dashboard.dto.request.DashboardScope;
import com.babylion.team1.domain.dashboard.dto.response.MetricResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetricCalculatorTest {

    private final List<CohortItem> items = new ArrayList<>();
    private int settingsVersion = 1;

    @Test
    @DisplayName("지표 5개를 직원이 확인한 값으로 계산하고 UNKNOWN과 미확인은 확인 수에서 뺀다")
    void calculatesFiveMetrics() {
        // 대상 30개: 결과 확인 25(성공 20), 재작업 확인 26(오류 수정 6)
        add(20, 1, SUCCESS, NONE, 4000);
        add(4, 1, FAILURE, ERROR_CORRECTION, 4000);
        add(1, 1, FAILURE, null, 4000);
        add(2, 1, UNKNOWN, ERROR_CORRECTION, 4000);
        add(2, 1, null, AnalysisReworkType.UNKNOWN, 4000);
        add(1, 1, null, null, 4000);

        assertMetric(CONFIRMED_SUCCESS_RATE, AVAILABLE, "0.8", 20L, 25L, 25L, 5L);
        assertMetric(OUTCOME_CONFIRMATION_RATE, AVAILABLE, "0.8333", 25L, 30L, 30L, 5L);
        assertMetric(ERROR_REWORK_RATE, AVAILABLE, "0.2308", 6L, 26L, 26L, 4L);
        assertMetric(REWORK_CONFIRMATION_RATE, AVAILABLE, "0.8667", 26L, 30L, 30L, 4L);
        assertMetric(TOKENS_PER_CONFIRMED_SUCCESS, AVAILABLE, "6000", 120000L, 20L, 30L, 0L);
    }

    @Test
    @DisplayName("판정 출처는 SELF_REPORT, 값 출처는 토큰 지표만 TOKENIZER_ESTIMATE다")
    void sources() {
        assertThat(MetricCalculator.calculate(DashboardScope.ME, items)).hasSize(5).allSatisfy(metric -> {
            boolean token = metric.code() == TOKENS_PER_CONFIRMED_SUCCESS;
            assertThat(metric.assessmentSource()).isEqualTo("SELF_REPORT");
            assertThat(metric.valueSource()).isEqualTo(
                    token ? MetricCode.ValueSource.TOKENIZER_ESTIMATE : MetricCode.ValueSource.USER_INPUT);
            assertThat(metric.unit()).isEqualTo(
                    token ? MetricCode.Unit.TOKENS_PER_SUCCESS : MetricCode.Unit.RATIO);
            assertThat(metric.reasonCodes()).isEmpty();
        });
    }

    @Test
    @DisplayName("분모 19개는 표본 부족, 20개는 정상이며 ME는 표본이 부족해도 건수를 준다")
    void sampleBoundary() {
        add(19, 1, SUCCESS, NONE, 1000);
        assertMetric(CONFIRMED_SUCCESS_RATE, INSUFFICIENT_SAMPLE, null, 19L, 19L, 19L, 0L);
        assertMetric(ERROR_REWORK_RATE, INSUFFICIENT_SAMPLE, null, 0L, 19L, 19L, 0L);
        assertMetric(TOKENS_PER_CONFIRMED_SUCCESS, INSUFFICIENT_SAMPLE, null, 19000L, 19L, 19L, 0L);

        add(1, 1, SUCCESS, NONE, 1000);
        assertMetric(CONFIRMED_SUCCESS_RATE, AVAILABLE, "1", 20L, 20L, 20L, 0L);
        assertMetric(ERROR_REWORK_RATE, AVAILABLE, "0", 0L, 20L, 20L, 0L);
        assertMetric(TOKENS_PER_CONFIRMED_SUCCESS, AVAILABLE, "1000", 20000L, 20L, 20L, 0L);
    }

    @Test
    @DisplayName("비율은 분모, 토큰은 대상 수로 표본을 판정한다")
    void sampleBasisDiffersByMetric() {
        add(19, 1, SUCCESS, NONE, 1000);
        add(1, 1, null, null, 1000);

        assertStatus(CONFIRMED_SUCCESS_RATE, INSUFFICIENT_SAMPLE);
        assertStatus(OUTCOME_CONFIRMATION_RATE, AVAILABLE);
        assertStatus(TOKENS_PER_CONFIRMED_SUCCESS, AVAILABLE);
    }

    @Test
    @DisplayName("결과 확인률이 정확히 80%면 토큰 지표를 제공하고, 미만이면 토큰 지표만 INSUFFICIENT_CONFIRMATION이다")
    void confirmationBoundary() {
        add(24, 1, SUCCESS, NONE, 1000);
        add(6, 1, null, NONE, 1000);
        assertStatus(TOKENS_PER_CONFIRMED_SUCCESS, AVAILABLE);

        add(1, 1, null, NONE, 1000);
        assertMetric(TOKENS_PER_CONFIRMED_SUCCESS, INSUFFICIENT_CONFIRMATION, null, 31000L, 24L, 31L, 0L);
        assertStatus(CONFIRMED_SUCCESS_RATE, AVAILABLE);
        assertStatus(OUTCOME_CONFIRMATION_RATE, AVAILABLE);
        assertStatus(ERROR_REWORK_RATE, AVAILABLE);
        assertStatus(REWORK_CONFIRMATION_RATE, AVAILABLE);
    }

    @Test
    @DisplayName("대상이 없으면 NO_DATA다")
    void noData() {
        for (MetricCode code : MetricCode.values()) {
            assertMetric(code, NO_DATA, null, 0L, 0L, 0L, 0L);
        }
    }

    @Test
    @DisplayName("대상은 있지만 분모가 0이면 표본 부족보다 ZERO_DENOMINATOR를 먼저 적용한다")
    void zeroDenominatorBeforeInsufficientSample() {
        add(3, 1, null, null, 1000);

        assertMetric(CONFIRMED_SUCCESS_RATE, ZERO_DENOMINATOR, null, 0L, 0L, 0L, 3L);
        assertMetric(ERROR_REWORK_RATE, ZERO_DENOMINATOR, null, 0L, 0L, 0L, 3L);
        assertMetric(TOKENS_PER_CONFIRMED_SUCCESS, ZERO_DENOMINATOR, null, 3000L, 0L, 3L, 0L);
        assertStatus(OUTCOME_CONFIRMATION_RATE, INSUFFICIENT_SAMPLE);
    }

    @Test
    @DisplayName("토큰을 측정하지 못한 대화가 있으면 ZERO_DENOMINATOR보다 NOT_MEASURED를 먼저 적용하고 분자는 null이다")
    void notMeasuredBeforeZeroDenominator() {
        add(3, 1, null, null, 1000);
        add(2, 1, null, null, null);

        assertMetric(TOKENS_PER_CONFIRMED_SUCCESS, NOT_MEASURED, null, null, 0L, 3L, 2L);
    }

    @Test
    @DisplayName("표본과 확인률이 모두 부족하면 INSUFFICIENT_SAMPLE을 먼저 적용한다")
    void insufficientSampleBeforeInsufficientConfirmation() {
        add(1, 1, SUCCESS, null, 1000);
        add(9, 1, null, null, 1000);

        assertStatus(TOKENS_PER_CONFIRMED_SUCCESS, INSUFFICIENT_SAMPLE);
    }

    @Test
    @DisplayName("기준 버전이 섞이면 NOT_COMPARABLE이고, TEAM에서는 SUPPRESSED를 먼저 적용한다")
    void notComparable() {
        add(1, 1, null, null, null);
        settingsVersion = 2;
        add(1, 1, null, null, null);

        for (MetricCode code : MetricCode.values()) {
            assertStatus(code, NOT_COMPARABLE);
            assertThat(metric(DashboardScope.TEAM, code).status()).isEqualTo(SUPPRESSED);
        }
    }

    @Test
    @DisplayName("TEAM은 기여자 4명이면 대상이 없어도 숨기고, 5명이면 제공한다. ME는 기여자 수를 보지 않는다")
    void contributorBoundary() {
        for (MetricCode code : MetricCode.values()) {
            assertSuppressed(code);
        }
        for (long userId = 1; userId <= 4; userId++) {
            add(6, userId, SUCCESS, NONE, 1000);
        }
        for (MetricCode code : MetricCode.values()) {
            assertSuppressed(code);
            assertStatus(code, AVAILABLE);
        }

        add(1, 5, SUCCESS, NONE, 1000);
        assertThat(MetricCalculator.calculate(DashboardScope.TEAM, items))
                .allMatch(metric -> metric.status() == AVAILABLE && metric.value() != null
                        && metric.denominator() == 25);
    }

    @Test
    @DisplayName("결과 확인 기여자만 부족하면 성공률을 역산할 수 있는 확인률과 토큰 지표도 숨긴다")
    void suppressesMetricsThatRevealSuccessRate() {
        for (long userId = 1; userId <= 4; userId++) {
            add(6, userId, SUCCESS, NONE, 1000);
        }
        add(1, 5, UNKNOWN, NONE, 1000);

        assertSuppressed(CONFIRMED_SUCCESS_RATE);
        assertSuppressed(OUTCOME_CONFIRMATION_RATE);
        assertSuppressed(TOKENS_PER_CONFIRMED_SUCCESS);
        assertThat(metric(DashboardScope.TEAM, ERROR_REWORK_RATE).status()).isEqualTo(AVAILABLE);
        assertThat(metric(DashboardScope.TEAM, REWORK_CONFIRMATION_RATE).status()).isEqualTo(AVAILABLE);
    }

    @Test
    @DisplayName("재작업 확인 기여자만 부족하면 재작업 확인률도 숨기고 결과·토큰 지표는 제공한다")
    void suppressesMetricsThatRevealReworkRate() {
        for (long userId = 1; userId <= 4; userId++) {
            add(6, userId, SUCCESS, NONE, 1000);
        }
        add(1, 5, SUCCESS, null, 1000);

        assertSuppressed(ERROR_REWORK_RATE);
        assertSuppressed(REWORK_CONFIRMATION_RATE);
        assertThat(metric(DashboardScope.TEAM, CONFIRMED_SUCCESS_RATE).status()).isEqualTo(AVAILABLE);
        assertThat(metric(DashboardScope.TEAM, OUTCOME_CONFIRMATION_RATE).status()).isEqualTo(AVAILABLE);
        assertThat(metric(DashboardScope.TEAM, TOKENS_PER_CONFIRMED_SUCCESS).status()).isEqualTo(AVAILABLE);
    }

    @Test
    @DisplayName("TEAM은 기여자가 충분해도 표본이 부족하면 건수를 주지 않는다")
    void teamHidesCountsWhenSampleIsInsufficient() {
        for (long userId = 1; userId <= 5; userId++) {
            add(1, userId, SUCCESS, NONE, 1000);
        }

        assertHidden(CONFIRMED_SUCCESS_RATE, INSUFFICIENT_SAMPLE);
    }

    private void add(int count, long userId, AnalysisOutcome outcome, AnalysisReworkType reworkType, Integer tokens) {
        for (int index = 0; index < count; index++) {
            long id = items.size() + 1L;
            items.add(new CohortItem(id, id, userId, LocalDate.of(2026, 11, 2), settingsVersion,
                    AnalysisItemStatus.SUCCEEDED, true, ScopeClassification.IN_SCOPE, outcome, reworkType, tokens));
        }
    }

    private MetricResponse metric(DashboardScope scope, MetricCode code) {
        return MetricCalculator.calculate(scope, items).stream()
                .filter(metric -> metric.code() == code)
                .findFirst()
                .orElseThrow();
    }

    private void assertStatus(MetricCode code, MetricStatus status) {
        assertThat(metric(DashboardScope.ME, code).status()).isEqualTo(status);
    }

    private void assertMetric(MetricCode code, MetricStatus status, String value, Long numerator, Long denominator,
            Long sampleSize, Long missingCount) {
        MetricResponse metric = metric(DashboardScope.ME, code);
        assertThat(metric.status()).isEqualTo(status);
        if (value == null) {
            assertThat(metric.value()).isNull();
        } else {
            assertThat(metric.value()).isEqualByComparingTo(new BigDecimal(value));
        }
        assertThat(metric.numerator()).isEqualTo(numerator);
        assertThat(metric.denominator()).isEqualTo(denominator);
        assertThat(metric.sampleSize()).isEqualTo(sampleSize);
        assertThat(metric.missingCount()).isEqualTo(missingCount);
    }

    private void assertSuppressed(MetricCode code) {
        assertHidden(code, SUPPRESSED);
    }

    private void assertHidden(MetricCode code, MetricStatus status) {
        MetricResponse metric = metric(DashboardScope.TEAM, code);
        assertThat(metric.status()).isEqualTo(status);
        assertThat(metric.value()).isNull();
        assertThat(metric.numerator()).isNull();
        assertThat(metric.denominator()).isNull();
        assertThat(metric.sampleSize()).isNull();
        assertThat(metric.missingCount()).isNull();
    }
}
