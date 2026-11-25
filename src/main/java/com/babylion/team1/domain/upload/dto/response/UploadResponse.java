package com.babylion.team1.domain.upload.dto.response;

import com.babylion.team1.domain.upload.entity.Upload.LifecycleStatus;
import java.time.OffsetDateTime;

public record UploadResponse(Long uploadId, LifecycleStatus status, int settingsVersion,
                             OffsetDateTime expiresAt, int conversationCount, int eligibleConversationCount,
                             String conversationsUrl) {
}
