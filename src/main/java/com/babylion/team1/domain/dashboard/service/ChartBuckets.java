package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.request.KpiUnit;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 차트의 점(버킷) 계산. DAY면 날짜 하나가 한 점, WEEK면 월요일 시작 한 주가 한 점이다.
 */
final class ChartBuckets {

    private ChartBuckets() {
    }

    /** DAY면 그날, WEEK면 그 주의 월요일 */
    static LocalDate bucketOf(LocalDate date, KpiUnit unit) {
        return unit == KpiUnit.WEEK ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : date;
    }

    /** 기간에 걸친 모든 점. 대화가 없는 날(주)도 빠짐없이 담기며 날짜 오름차순이다. */
    static List<LocalDate> bucketsIn(DatePeriod period, KpiUnit unit) {
        return period.from().datesUntil(period.to().plusDays(1))
                .map(date -> bucketOf(date, unit))
                .distinct()
                .toList();
    }
}
