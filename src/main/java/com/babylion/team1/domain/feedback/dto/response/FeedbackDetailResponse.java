package com.babylion.team1.domain.feedback.dto.response;

import com.babylion.team1.domain.analysis.dto.AnalysisItemsResponse.AiAssessment;
import com.babylion.team1.domain.analysis.dto.AnalysisItemsResponse.OutcomeAssessment;
import com.babylion.team1.domain.analysis.dto.AnalysisItemsResponse.ReworkAssessment;
import com.babylion.team1.domain.feedback.entity.ReactionType;
import java.time.OffsetDateTime;
import java.util.List;

public record FeedbackDetailResponse(Long feedbackId, Long itemId, Long conversationId,
        Long conversationVersionId, String type, String title, String problem, List<String> causes,
        String improvement, FeedbackExample example, List<String> evidenceMessageIds, List<String> limitations,
        AiAssessment aiAssessment, OutcomeAssessment outcomeAssessment, ReworkAssessment reworkAssessment,
        ReactionType reaction, Sharing sharing, ExpectedSaving expectedSaving, UsageSummary usageSummary) {
    public record Sharing(String status, int version, Long sharedCaseId) {}
    public record ExpectedSaving(String status, Long value, String reasonCode) {
        public static final ExpectedSaving NOT_MEASURED = new ExpectedSaving("NOT_MEASURED", null, "NO_VALIDATED_ESTIMATE");
    }
    public record UsageSummary(OffsetDateTime copySucceededAt, OffsetDateTime useConfirmedAt) {}
}
