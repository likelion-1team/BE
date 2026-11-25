package com.babylion.team1.domain.feedback.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record MyFeedbackListResponse(List<Item> items, int page, int size, long total) {
    public record Item(Long feedbackId, Long itemId, Long conversationId, String type, String title,
                       long occurrenceCount, OffsetDateTime createdAt, String reaction, String sharingStatus,
                       FeedbackDetailResponse.ExpectedSaving expectedSaving) {}
}
