package com.babylion.team1.domain.dashboard.dto.request;

import com.babylion.team1.domain.dashboard.dto.ConversationAggregate;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Arrays;

/**
 * 추이 차트에서 고를 수 있는 지표. 요청·응답 값은 KPI 응답 필드명과 같은 camelCase(예: tokensPerSuccess)다.
 * 값 계산은 KPI 카드와 같은 ConversationAggregate 로직을 쓴다. 분모가 0이면 null.
 */
public enum TrendMetric {

    TOKENS_PER_SUCCESS("tokensPerSuccess", true),
    REWORK_RATE("reworkRate", false),
    SUCCESS_RATE("successRate", false),
    COST_PER_SUCCESS("costPerSuccess", true),
    HUMAN_TIME("humanTime", false),
    CONVERSATION_COUNT("conversationCount", false);

    private final String value;
    private final boolean estimated;

    TrendMetric(String value, boolean estimated) {
        this.value = value;
        this.estimated = estimated;
    }

    public String value() {
        return value;
    }

    /** 추정 토큰·추정 호출비를 바탕으로 계산한 지표인지 여부 */
    public boolean estimated() {
        return estimated;
    }

    /** 없거나 목록에 없는 값이면 400 INVALID_REQUEST */
    public static TrendMetric from(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "metric은 필수입니다.");
        }
        return Arrays.stream(values())
                .filter(metric -> metric.value.equals(value.trim()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST, "지원하지 않는 metric입니다."));
    }

    public BigDecimal calculate(ConversationAggregate aggregate, int hourlyLaborCost) {
        return switch (this) {
            case TOKENS_PER_SUCCESS -> aggregate.tokensPerSuccess();
            case REWORK_RATE -> aggregate.reworkRate();
            case SUCCESS_RATE -> aggregate.successRate();
            case COST_PER_SUCCESS -> aggregate.costPerSuccess(hourlyLaborCost);
            case HUMAN_TIME -> BigDecimal.valueOf(aggregate.humanTimeSec());
            case CONVERSATION_COUNT -> BigDecimal.valueOf(aggregate.conversationCount());
        };
    }
}
