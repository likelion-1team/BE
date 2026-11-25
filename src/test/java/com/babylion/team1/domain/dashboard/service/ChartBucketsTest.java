package com.babylion.team1.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.request.KpiUnit;
import java.time.LocalDate;
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
    @DisplayName("DAY면 기간의 모든 날이 날짜 오름차순으로 담긴다")
    void bucketsInByDay() {
        DatePeriod period = new DatePeriod(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 2));

        assertThat(ChartBuckets.bucketsIn(period, KpiUnit.DAY)).containsExactly(
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2));
    }

    @Test
    @DisplayName("WEEK면 기간에 걸친 주의 월요일이 한 번씩 담긴다")
    void bucketsInByWeek() {
        // 11-04(수) ~ 11-17(화)
        DatePeriod period = new DatePeriod(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 17));

        assertThat(ChartBuckets.bucketsIn(period, KpiUnit.WEEK)).containsExactly(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16));
    }
}
