package com.babylion.team1.domain.feedback.dto.response;

import com.babylion.team1.domain.feedback.entity.FeedbackUsageEvent;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record FeedbackUsageResponse(String eventId, FeedbackUsageEvent.Type eventType, OffsetDateTime recordedAt) {
    public static FeedbackUsageResponse from(FeedbackUsageEvent event) {
        return new FeedbackUsageResponse(event.getEventId(), event.getEventType(),
                event.getRecordedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }
}
