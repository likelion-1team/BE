package com.babylion.team1.domain.upload.dto.response;

import com.babylion.team1.domain.upload.entity.Upload.LifecycleStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

public record UploadStatusResponse(LifecycleStatus status,
                                   @JsonInclude(JsonInclude.Include.ALWAYS) Long analysisId) {
}
