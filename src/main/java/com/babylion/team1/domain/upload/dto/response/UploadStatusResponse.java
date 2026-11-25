package com.babylion.team1.domain.upload.dto.response;

import com.babylion.team1.domain.upload.entity.Upload.LifecycleStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UploadStatusResponse(Long uploadId, LifecycleStatus status, OffsetDateTime createdAt,
                                   OffsetDateTime expiresAt, Integer settingsVersion, Long analysisId,
                                   CleanupStatus cleanupStatus) {
    public enum CleanupStatus { PENDING, COMPLETED }
}
