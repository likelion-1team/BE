package com.babylion.team1.domain.dashboard.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 비용 집계 쿼리의 한 행: 하루 x 그룹 키(모델명·업무 유형명, COST_TYPE이면 null)의 호출비 합과 검토·수습 시간 합.
 * 비용(원) 계산은 반올림 전 정밀도로 하고, 응답 직전에만 원 단위로 반올림한다.
 */
public record CostRow(
        LocalDate date,
        String key,
        BigDecimal callCost,
        long reviewTimeSec,
        long reworkTimeSec
) {

    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);
    private static final int SCALE = 4;

    public BigDecimal reviewCost(int hourlyLaborCost) {
        return laborCost(reviewTimeSec, hourlyLaborCost);
    }

    public BigDecimal reworkCost(int hourlyLaborCost) {
        return laborCost(reworkTimeSec, hourlyLaborCost);
    }

    /** 호출비 + 검토비 + 수습비 */
    public BigDecimal totalCost(int hourlyLaborCost) {
        return callCost.add(reviewCost(hourlyLaborCost)).add(reworkCost(hourlyLaborCost));
    }

    private static BigDecimal laborCost(long seconds, int hourlyLaborCost) {
        return BigDecimal.valueOf(seconds)
                .multiply(BigDecimal.valueOf(hourlyLaborCost))
                .divide(SECONDS_PER_HOUR, SCALE, RoundingMode.HALF_UP);
    }
}
