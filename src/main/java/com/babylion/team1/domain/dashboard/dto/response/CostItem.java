package com.babylion.team1.domain.dashboard.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 누적 막대 한 칸. cost는 원 단위 정수(반올림) */
public record CostItem(
        String key,
        long cost
) {

    public static CostItem of(String key, BigDecimal cost) {
        return new CostItem(key, cost.setScale(0, RoundingMode.HALF_UP).longValueExact());
    }
}
