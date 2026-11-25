package com.babylion.team1.domain.analysis.dto;

import com.babylion.team1.domain.analysis.entity.AnalysisOutcome;
import com.babylion.team1.domain.analysis.entity.OutcomeAssessment;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record OutcomeAssessmentResponse(Long itemId, int version, AnalysisOutcome outcome,
                                        String source, OffsetDateTime assessedAt) {
    public static OutcomeAssessmentResponse from(OutcomeAssessment assessment) {
        return new OutcomeAssessmentResponse(assessment.getItem().getId(), assessment.getVersion(),
                assessment.getOutcome(), assessment.getSource(),
                assessment.getAssessedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }
}
