package com.babylion.team1.domain.team.dto.response;

public record TeamSubmissionResponse(
        long submittedMembers,
        long totalMembers,
        long analyzedConversations,
        Integer targetCount
) {
}
