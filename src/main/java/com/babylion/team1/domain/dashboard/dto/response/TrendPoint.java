package com.babylion.team1.domain.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 추이 차트의 점 하나.
 * date: DAY면 그날, WEEK면 그 주의 월요일. 대화가 없는 날(주)도 빠짐없이 내려준다.
 * value: 분모가 0이면 null. insufficientSample: 대화 수가 표본 부족 기준보다 적으면 true.
 */
public record TrendPoint(
        LocalDate date,
        BigDecimal value,
        long conversationCount,
        boolean insufficientSample
) {
}
