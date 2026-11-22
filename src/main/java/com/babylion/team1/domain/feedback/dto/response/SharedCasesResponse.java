package com.babylion.team1.domain.feedback.dto.response;

import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.FeedbackType;
import java.util.List;

public record SharedCasesResponse(List<Item> items) {

    public static SharedCasesResponse from(List<Feedback> feedbacks) {
        return new SharedCasesResponse(feedbacks.stream().map(Item::from).toList());
    }

    public record Item(
            Long feedbackId,
            String taskType,
            String type,
            String title,
            String problem,
            String improvement,
            FeedbackExample example,
            boolean isGuide
    ) {

        private static Item from(Feedback feedback) {
            return new Item(
                    feedback.getId(),
                    feedback.getTaskType().getName(),
                    FeedbackType.displayName(feedback.getType()),
                    feedback.getTitle(),
                    feedback.getProblem(),
                    feedback.getImprovement(),
                    FeedbackExample.from(feedback),
                    Boolean.TRUE.equals(feedback.getIsGuide()));
        }
    }
}
