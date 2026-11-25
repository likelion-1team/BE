package com.babylion.team1.domain.team.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TeamSubmissionResponse(
        int settingsVersion,
        long submittedMembers,
        long totalMembers,
        Long analyzedConversations,
        Integer targetCount,
        BigDecimal coverageRatio,
        Status status
) {
    public enum Status { AVAILABLE, SUPPRESSED }
}
