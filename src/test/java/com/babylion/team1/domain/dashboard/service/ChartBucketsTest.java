package com.babylion.team1.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.request.KpiUnit;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChartBucketsTest {

    @Test
    @DisplayName("DAY면 그날, WEEK면 그 주 월요일이 점이 된다")
    void bucketOf() {
        LocalDate wednesday = LocalDate.of(2026, 11, 4);

        assertThat(ChartBuckets.bucketOf(wednesday, KpiUnit.DAY)).isEqualTo(wednesday);
        assertThat(ChartBuckets.bucketOf(wednesday, KpiUnit.WEEK)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(ChartBuckets.bucketOf(LocalDate.of(2026, 11, 2), KpiUnit.WEEK)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(ChartBuckets.bucketOf(LocalDate.of(2026, 11, 8), KpiUnit.WEEK)).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    @DisplayName("DAY면 기간의 모든 날이 1일씩 날짜 오름차순으로 담긴다")
    void daysPerBucketByDay() {
        DatePeriod period = new DatePeriod(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 2));

        Map<LocalDate, Integer> days = ChartBuckets.daysPerBucket(period, KpiUnit.DAY);

        assertThat(days).containsExactly(
                Map.entry(LocalDate.of(2026, 11, 30), 1),
                Map.entry(LocalDate.of(2026, 12, 1), 1),
                Map.entry(LocalDate.of(2026, 12, 2), 1));
    }

    @Test
    @DisplayName("WEEK면 양 끝 주는 기간 안에 든 날만 센다")
    void daysPerBucketByWeekCountsOnlyDaysInPeriod() {
        // 11-04(수) ~ 11-17(화): 첫 주 11-04~11-08(5일), 둘째 주 11-09~11-15(7일), 셋째 주 11-16~11-17(2일)
        DatePeriod period = new DatePeriod(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 17));

        Map<LocalDate, Integer> days = ChartBuckets.daysPerBucket(period, KpiUnit.WEEK);

        assertThat(days).containsExactly(
                Map.entry(LocalDate.of(2026, 11, 2), 5),
                Map.entry(LocalDate.of(2026, 11, 9), 7),
                Map.entry(LocalDate.of(2026, 11, 16), 2));
    }
}
