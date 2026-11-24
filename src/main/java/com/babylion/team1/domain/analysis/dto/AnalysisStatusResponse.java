package com.babylion.team1.domain.analysis.dto;

import java.time.OffsetDateTime;

public record AnalysisStatusResponse(
        long analysisId,
        long uploadId,
        String status,
        int settingsVersion,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String stage,
        int selectedCount,
        int completedCount,
        int succeededCount,
        int failedCount,
        int progressPercent,
        int pollAfterSeconds,
        String itemsUrl
) {
}
