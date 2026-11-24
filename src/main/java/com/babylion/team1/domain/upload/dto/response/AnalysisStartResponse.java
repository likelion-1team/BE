package com.babylion.team1.domain.upload.dto.response;

import com.babylion.team1.domain.analysis.entity.AnalysisStatus;

public record AnalysisStartResponse(Long analysisId, Long uploadId, AnalysisStatus status,
                                    int selectedCount, String statusUrl, String itemsUrl, int pollAfterSeconds) {
}
