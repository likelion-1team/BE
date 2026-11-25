package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.FeedbackUsageEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackUsageEventRepository extends JpaRepository<FeedbackUsageEvent, Long> {
    Optional<FeedbackUsageEvent> findFirstByFeedbackIdAndUserIdAndEventTypeOrderByRecordedAtDesc(
            Long feedbackId, Long userId, FeedbackUsageEvent.Type eventType);

    Optional<FeedbackUsageEvent> findByUserIdAndEventId(Long userId, String eventId);
}
