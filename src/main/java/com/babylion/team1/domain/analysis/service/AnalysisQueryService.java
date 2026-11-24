package com.babylion.team1.domain.analysis.service;

import com.babylion.team1.domain.analysis.dto.AnalysisStatusResponse;
import com.babylion.team1.domain.analysis.entity.Analysis;
import com.babylion.team1.domain.analysis.repository.AnalysisRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisQueryService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AnalysisRepository analyses;

    public AnalysisStatusResponse getStatus(Long analysisId, Long userId) {
        Analysis analysis = requireOwnedAnalysis(analysisId, userId);
        int selected = analysis.getSelectedCount();
        int completed = analysis.getCompletedCount();
        int progress = selected == 0 ? 0 : (int) (100L * completed / selected);
        return new AnalysisStatusResponse(analysisId, analysis.getUpload().getId(),
                analysis.getStatus().name(), analysis.getSettings().getVersion(), inKst(analysis.getCreatedAt()),
                inKst(analysis.getStartedAt()), inKst(analysis.getFinishedAt()), analysis.getStage(),
                selected, completed, analysis.getSucceededCount(), analysis.getFailedCount(), progress,
                3, "/api/v1/analyses/" + analysisId + "/items");
    }

    private Analysis requireOwnedAnalysis(Long analysisId, Long userId) {
        return analyses.findByIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static OffsetDateTime inKst(Instant instant) {
        return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
    }
}
