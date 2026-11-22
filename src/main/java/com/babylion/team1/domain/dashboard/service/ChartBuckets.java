package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.request.KpiUnit;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * 기간 안의 모든 점과 점마다 기간 안에 든 일수. 대화가 없는 날(주)도 빠짐없이 담기며 날짜 오름차순이다.
     * WEEK에서 기간 양 끝의 주는 기간 안에 든 날만 센다(예: 수요일 시작이면 첫 주는 5일).
     */
    static Map<LocalDate, Integer> daysPerBucket(DatePeriod period, KpiUnit unit) {
        Map<LocalDate, Integer> days = new LinkedHashMap<>();
        for (LocalDate date = period.from(); !date.isAfter(period.to()); date = date.plusDays(1)) {
            days.merge(bucketOf(date, unit), 1, Integer::sum);
        }
        return days;
    }
}
