package com.babylion.team1.domain.feedback.dto.response;

import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Content;
import com.babylion.team1.domain.feedback.entity.FeedbackType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GuidesResponse(List<Group> groups) {

    public static GuidesResponse from(List<Feedback> feedbacks, Map<Long, Content> snapshots) {
        Map<String, List<Guide>> guidesByType = new LinkedHashMap<>();
        for (Feedback feedback : feedbacks) {
            String type = FeedbackType.displayName(feedback.getType());
            guidesByType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(Guide.from(feedback, snapshots.get(feedback.getId())));
        }
        return new GuidesResponse(guidesByType.entrySet().stream()
                .map(entry -> new Group(entry.getKey(), entry.getValue()))
                .toList());
    }

    public record Group(String type, List<Guide> guides) {
    }

    public record Guide(
            Long feedbackId,
            String title,
            String problem,
            String improvement,
            FeedbackExample example,
            OffsetDateTime guideAddedAt
    ) {

        private static Guide from(Feedback feedback, Content snapshot) {
            return new Guide(
                    feedback.getId(),
                    snapshot == null ? feedback.getTitle() : snapshot.title(),
                    snapshot == null ? feedback.getProblem() : snapshot.problem(),
                    snapshot == null ? feedback.getImprovement() : snapshot.improvement(),
                    snapshot == null ? FeedbackExample.from(feedback)
                            : new FeedbackExample(snapshot.example().before(), snapshot.example().after()),
                    feedback.guideAddedAtInKst());
        }
    }
}
