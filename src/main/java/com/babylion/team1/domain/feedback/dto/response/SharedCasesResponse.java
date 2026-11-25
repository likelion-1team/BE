package com.babylion.team1.domain.feedback.dto.response;

import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Content;
import com.babylion.team1.domain.feedback.entity.FeedbackType;
import java.util.List;
import java.util.Map;

public record SharedCasesResponse(List<Item> items) {

    public static SharedCasesResponse from(List<Feedback> feedbacks, Map<Long, Content> snapshots) {
        return new SharedCasesResponse(feedbacks.stream().map(feedback -> Item.from(feedback, snapshots.get(feedback.getId()))).toList());
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

        private static Item from(Feedback feedback, Content snapshot) {
            return new Item(
                    feedback.getId(),
                    feedback.getTaskType().getName(),
                    FeedbackType.displayName(feedback.getType()),
                    snapshot == null ? feedback.getTitle() : snapshot.title(),
                    snapshot == null ? feedback.getProblem() : snapshot.problem(),
                    snapshot == null ? feedback.getImprovement() : snapshot.improvement(),
                    snapshot == null ? FeedbackExample.from(feedback)
                            : new FeedbackExample(snapshot.example().before(), snapshot.example().after()),
                    Boolean.TRUE.equals(feedback.getIsGuide()));
        }
    }
}
