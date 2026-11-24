package com.babylion.team1.domain.analysis.dto;

import com.babylion.team1.domain.analysis.entity.AnalysisReworkType;
import com.babylion.team1.domain.analysis.entity.ReworkAssessment;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ReworkAssessmentResponse(Long itemId, int version, AnalysisReworkType type,
                                        String source, OffsetDateTime assessedAt) {
    public static ReworkAssessmentResponse from(ReworkAssessment assessment) {
        return new ReworkAssessmentResponse(assessment.getItem().getId(), assessment.getVersion(),
                assessment.getType(), assessment.getSource(),
                assessment.getAssessedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }
}
