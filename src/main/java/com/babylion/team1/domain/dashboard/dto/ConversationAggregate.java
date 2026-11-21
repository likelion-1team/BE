package com.babylion.team1.domain.dashboard.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 한 기간의 conversations 집계 결과. 분모가 0인 지표는 null을 돌려준다. */
public record ConversationAggregate(
        long conversationCount,
        long successCount,
        long reworkCount,
        long totalTokens,
        BigDecimal totalCallCost,
        long reviewTimeSec,
        long reworkTimeSec,
        long submittedMemberCount
) {

    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    public static ConversationAggregate empty() {
        return new ConversationAggregate(0, 0, 0, 0, BigDecimal.ZERO, 0, 0, 0);
    }

    /** 추정 토큰 합(성공·실패 모두) / 성공 업무 수 */
    public BigDecimal tokensPerSuccess() {
        return divide(BigDecimal.valueOf(totalTokens), successCount, 2);
    }

    /** has_rework 대화 수 / 전체 대화 수 (0~1) */
    public BigDecimal reworkRate() {
        return divide(BigDecimal.valueOf(reworkCount), conversationCount, 4);
    }

    /** 성공 업무 수 / 전체 업무 수 (0~1). 대화 1개 = 업무 1건 */
    public BigDecimal successRate() {
        return divide(BigDecimal.valueOf(successCount), conversationCount, 4);
    }

    /** (호출비 + 검토비 + 수습비) / 성공 업무 수. 검토비·수습비 = 시간 x 시간당 인건비 */
    public BigDecimal costPerSuccess(int hourlyLaborCost) {
        BigDecimal laborCost = BigDecimal.valueOf(humanTimeSec())
                .multiply(BigDecimal.valueOf(hourlyLaborCost))
                .divide(SECONDS_PER_HOUR, 4, RoundingMode.HALF_UP);
        return divide(totalCallCost.add(laborCost), successCount, 2);
    }

    /** 사람 투입 시간(검토 + 수습) 합(초) */
    public long humanTimeSec() {
        return reviewTimeSec + reworkTimeSec;
    }

    private static BigDecimal divide(BigDecimal numerator, long denominator, int scale) {
        if (denominator == 0) {
            return null;
        }
        return numerator.divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }
}
