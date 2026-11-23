package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.dashboard.dto.ConversationAggregate;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 업무 유형 하나의 집계. 팀원 개인을 식별할 수 있는 정보(user_id, 이름 등)는 담지 않는다.
 * reworkRate·tokensPerSuccess는 KPI 카드와 같은 계산식이며 분모가 0이면 null.
 * topFeedbackTypes: 같은 기간 피드백 유형을 건수 많은 순으로 최대 3개.
 */
public record TaskTypeStatsItem(
        TaskTypeOption taskType,
        BigDecimal reworkRate,
        BigDecimal tokensPerSuccess,
        long conversationCount,
        List<String> topFeedbackTypes
) {

    /** 재작업률 높은 순(null은 맨 뒤), 같으면 대화 수 많은 순, 그다음 업무 유형 id 순 */
    public static final Comparator<TaskTypeStatsItem> REWORK_RATE_DESC = Comparator
            .comparing(TaskTypeStatsItem::reworkRate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TaskTypeStatsItem::conversationCount, Comparator.reverseOrder())
            .thenComparing(item -> item.taskType().id());

    public static TaskTypeStatsItem of(TaskTypeOption taskType, ConversationAggregate aggregate,
            List<String> topFeedbackTypes) {
        return new TaskTypeStatsItem(
                taskType,
                aggregate.reworkRate(),
                aggregate.tokensPerSuccess(),
                aggregate.conversationCount(),
                List.copyOf(topFeedbackTypes));
    }
}
