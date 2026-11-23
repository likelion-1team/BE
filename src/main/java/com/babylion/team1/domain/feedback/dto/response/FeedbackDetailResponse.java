package com.babylion.team1.domain.feedback.dto.response;

import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.FeedbackEvidence;
import com.babylion.team1.domain.feedback.entity.FeedbackType;
import java.math.BigDecimal;
import java.util.List;

public record FeedbackDetailResponse(
        Long feedbackId,
        String type,
        String title,
        String problem,
        List<String> causes,
        String improvement,
        FeedbackExample example,
        ExpectedSaving expectedSaving,
        List<Evidence> evidences
) {

    public static FeedbackDetailResponse from(Feedback feedback, List<FeedbackEvidence> evidences) {
        return new FeedbackDetailResponse(
                feedback.getId(), FeedbackType.displayName(feedback.getType()), feedback.getTitle(),
                feedback.getProblem(), feedback.getCauses(), feedback.getImprovement(),
                FeedbackExample.from(feedback),
                new ExpectedSaving(feedback.getExpectedSavingTokens(), feedback.getExpectedSavingCost(),
                        feedback.getExpectedSavingTurns()),
                evidences.stream()
                        .map(evidence -> new Evidence(evidence.getConversation().getId(), evidence.getTurnIndexes()))
                        .toList());
    }

    public record ExpectedSaving(int tokens, BigDecimal cost, int turns) {
    }

    public record Evidence(Long conversationId, List<Integer> turnIndexes) {
    }
}
