package com.babylion.team1.domain.dashboard.dto.response;

import java.math.BigDecimal;

/**
 * KPI 카드 값 하나.
 * value: 현재 기간 값, prevValue: 직전 같은 길이 기간 값. 분모가 0이면 null.
 * estimated: 추정치(추정 토큰·추정 호출비·예상 절감액)를 바탕으로 계산한 값인지 여부.
 */
public record MetricValue(
        BigDecimal value,
        BigDecimal prevValue,
        boolean estimated
) {

    public static MetricValue of(BigDecimal value, BigDecimal prevValue, boolean estimated) {
        return new MetricValue(value, prevValue, estimated);
    }

    public static MetricValue of(long value, long prevValue, boolean estimated) {
        return new MetricValue(BigDecimal.valueOf(value), BigDecimal.valueOf(prevValue), estimated);
    }
}
