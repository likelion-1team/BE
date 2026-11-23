package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.dashboard.dto.ConversationAggregate;
import java.math.BigDecimal;

/**
 * 일자 상세(팀장용)의 업무 유형 하나. 개인 식별 정보(user_id, 대화 제목 등)는 담지 않는다.
 * reworkRate·tokensPerSuccess는 KPI 카드와 같은 계산식이며 분모가 0이면 null.
 */
public record DayTaskTypeItem(
        String name,
        long count,
        BigDecimal reworkRate,
        BigDecimal tokensPerSuccess
) {

    public static DayTaskTypeItem of(String name, ConversationAggregate aggregate) {
        return new DayTaskTypeItem(name, aggregate.conversationCount(), aggregate.reworkRate(),
                aggregate.tokensPerSuccess());
    }
}
