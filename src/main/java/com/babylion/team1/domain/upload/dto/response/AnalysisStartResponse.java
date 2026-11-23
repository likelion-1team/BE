package com.babylion.team1.domain.upload.dto.response;

import com.babylion.team1.domain.upload.entity.UploadStatus;

public record AnalysisStartResponse(Long uploadId, UploadStatus status) {
}
