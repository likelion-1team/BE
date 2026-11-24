package com.babylion.team1.domain.analysis.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AnalysisItemsResponse(List<Item> items, int page, int size, long total) {

    /** conversationId identifies the logical conversation. conversationVersionId identifies the analyzed snapshot. */
    public record Item(long itemId, long conversationId, long conversationVersionId,
                       String status, String scopeClassification, String failureCode,
                       List<Long> feedbackIds, AiAssessment aiAssessment,
                       OutcomeAssessment outcomeAssessment, ReworkAssessment reworkAssessment) {
    }

    public record AiAssessment(String outcome, String reworkType, List<String> evidenceMessageIds,
                               String modelVersion, String rubricVersion) {
    }

    public record OutcomeAssessment(int version, String outcome, String source, OffsetDateTime assessedAt) {
    }

    public record ReworkAssessment(int version, String type, String source, OffsetDateTime assessedAt) {
    }
}
