package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.dashboard.dto.ConversationAggregate;
import java.math.BigDecimal;

/**
 * 효과 측정용 핵심 지표 3개. 계산은 KPI 카드와 같은 ConversationAggregate 로직을 쓴다.
 * 분모가 0이면 해당 지표는 null(대화가 0건이면 3개 모두 null).
 * conversationCount는 비교 표본 크기를 화면에 보여줄 수 있도록 함께 내려준다.
 */
public record CoreMetrics(
        long conversationCount,
        BigDecimal tokensPerSuccess,
        BigDecimal reworkRate,
        BigDecimal successRate
) {

    public static CoreMetrics from(ConversationAggregate aggregate) {
        return new CoreMetrics(
                aggregate.conversationCount(),
                aggregate.tokensPerSuccess(),
                aggregate.reworkRate(),
                aggregate.successRate());
    }
}
