package com.babylion.team1.domain.dashboard.dto.response;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 분석 범위(팀장 전용). 필드 이름은 팀 제출 현황 API(GET /teams/{teamId}/submissions)와 맞춘다.
 * coverageRate = analyzedConversations / targetCount (0~1). targetCount가 없거나 0이면 null.
 */
public record CoverageResponse(
        long submittedMembers,
        long totalMembers,
        long analyzedConversations,
        Integer targetCount,
        BigDecimal coverageRate
) {

    public static CoverageResponse of(long submittedMembers, long totalMembers,
            long analyzedConversations, Integer targetCount) {
        BigDecimal coverageRate = (targetCount == null || targetCount == 0)
                ? null
                : BigDecimal.valueOf(analyzedConversations)
                        .divide(BigDecimal.valueOf(targetCount), 4, RoundingMode.HALF_UP);
        return new CoverageResponse(submittedMembers, totalMembers, analyzedConversations,
                targetCount, coverageRate);
    }
}
