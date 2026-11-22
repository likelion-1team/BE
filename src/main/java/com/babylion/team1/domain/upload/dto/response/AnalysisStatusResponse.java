package com.babylion.team1.domain.upload.dto.response;

import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

public record AnalysisStatusResponse(
        UploadStatus status,
        int progress,
        @JsonInclude(JsonInclude.Include.ALWAYS) String failReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long savedCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer skippedDuplicateCount
) {
}
