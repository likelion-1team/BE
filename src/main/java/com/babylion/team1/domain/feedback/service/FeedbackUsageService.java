package com.babylion.team1.domain.feedback.service;

import com.babylion.team1.domain.feedback.dto.request.FeedbackUsageRequest;
import com.babylion.team1.domain.feedback.dto.response.FeedbackUsageResponse;
import com.babylion.team1.domain.feedback.entity.FeedbackUsageEvent;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackUsageEventRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackUsageService {
    private final EntityManager entityManager;
    private final FeedbackRepository feedbacks;
    private final FeedbackUsageEventRepository events;

    @Transactional
    public FeedbackUsageResponse record(Long feedbackId, Long userId, FeedbackUsageRequest request) {
        String eventId = parseEventId(request.eventId());
        var type = parseType(request.eventType());
        // Serialize a user's event IDs even when concurrent requests target different feedbacks.
        if (entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        var feedback = feedbacks.findByIdForUpdate(feedbackId).filter(row -> row.isOwnedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        var previous = events.findByUserIdAndEventId(userId, eventId);
        if (previous.isPresent()) {
            var event = previous.get();
            if (!event.getFeedback().getId().equals(feedbackId) || event.getEventType() != type) {
                throw new CustomException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return FeedbackUsageResponse.from(event);
        }
        return FeedbackUsageResponse.from(events.save(new FeedbackUsageEvent(feedback, eventId, type)));
    }

    private static String parseEventId(String value) {
        if (value != null && value.length() == 36) {
            try {
                String canonical = UUID.fromString(value).toString();
                if (canonical.equalsIgnoreCase(value)) {
                    return canonical;
                }
            } catch (IllegalArgumentException ignored) {
                // The API returns the same validation error for missing and malformed IDs.
            }
        }
        throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }

    private static FeedbackUsageEvent.Type parseType(String value) {
        if (value != null) {
            try {
                return FeedbackUsageEvent.Type.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                // Only the documented types are accepted.
            }
        }
        throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }
}
