package com.babylion.team1.domain.dashboard.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 한 기간의 conversations 집계 결과. 분모가 0인 지표는 null을 돌려준다. */
public record ConversationAggregate(
        long conversationCount,
        long successCount,
        long reworkCount,
        long totalTokens
) {

    public static ConversationAggregate empty() {
        return new ConversationAggregate(0, 0, 0, 0);
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

    private static BigDecimal divide(BigDecimal numerator, long denominator, int scale) {
        if (denominator == 0) {
            return null;
        }
        return numerator.divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }
}
