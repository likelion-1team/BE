package com.babylion.team1.domain.team.dto.response;

import com.babylion.team1.domain.team.entity.TeamGoal;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public record TeamGoalResponse(
        int version,
        BigDecimal successRateMin,
        BigDecimal errorReworkRateMax,
        Integer tokensPerSuccessMax,
        OffsetDateTime updatedAt
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static TeamGoalResponse from(TeamGoal goal) {
        OffsetDateTime updatedAt = goal.getUpdatedAt() == null ? null
                : goal.getUpdatedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(KST).toOffsetDateTime();
        return new TeamGoalResponse(goal.getVersion(), goal.getSuccessRateMin(), goal.getErrorReworkRateMax(),
                goal.getTokensPerSuccessMax(), updatedAt);
    }

    public static TeamGoalResponse unset() {
        return new TeamGoalResponse(0, null, null, null, null);
    }
}
