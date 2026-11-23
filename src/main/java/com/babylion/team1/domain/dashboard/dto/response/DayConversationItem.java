package com.babylion.team1.domain.dashboard.dto.response;

import java.time.LocalDateTime;

/** 일자 상세(팀원용)의 본인 대화 하나. taskType은 업무 유형명 */
public record DayConversationItem(
        Long conversationId,
        String title,
        LocalDateTime startedAt,
        String model,
        String taskType,
        int turnCount,
        int estimatedTokens,
        boolean isSuccess,
        boolean hasRework
) {
}
