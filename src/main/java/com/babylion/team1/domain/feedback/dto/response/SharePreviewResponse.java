package com.babylion.team1.domain.feedback.dto.response;

import java.time.OffsetDateTime;

public record SharePreviewResponse(Long previewId, int previewVersion, OffsetDateTime expiresAt,
                                   String redactionStatus, Content content) {
    public record Content(String title, String type, String problem, String improvement, Example example) {
    }

    public record Example(String before, String after) {
    }
}
