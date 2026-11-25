package com.babylion.team1.domain.upload.dto.response;

import com.babylion.team1.domain.analysis.entity.AnalysisStatus;
import com.babylion.team1.domain.upload.entity.Upload.LifecycleStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record UploadHistoryResponse(List<Item> items, int page, int size, long total) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Item(Long uploadId, OffsetDateTime createdAt, LifecycleStatus status, Long analysisId,
                       AnalysisStatus analysisStatus, Integer selectedCount, Long succeededCount,
                       Integer failedCount, LocalDate targetFrom, LocalDate targetTo) {
    }
}
