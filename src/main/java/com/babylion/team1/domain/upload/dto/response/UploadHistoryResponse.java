package com.babylion.team1.domain.upload.dto.response;

import com.babylion.team1.domain.analysis.entity.AnalysisStatus;
import com.babylion.team1.domain.upload.entity.Upload.LifecycleStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;

public record UploadHistoryResponse(List<Item> content, int page, int size, long totalElements) {

    public record Item(
            Long uploadId,
            String uploadedAt,
            long analyzedCount,
            @JsonInclude(JsonInclude.Include.ALWAYS) Period period,
            LifecycleStatus status,
            @JsonInclude(JsonInclude.Include.ALWAYS) Long analysisId,
            @JsonInclude(JsonInclude.Include.ALWAYS) AnalysisStatus analysisStatus,
            @JsonInclude(JsonInclude.Include.ALWAYS) Integer succeededCount,
            @JsonInclude(JsonInclude.Include.ALWAYS) Integer failedCount
    ) {
    }

    public record Period(LocalDate from, LocalDate to) {
    }
}
