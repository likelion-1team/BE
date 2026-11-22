package com.babylion.team1.domain.feedback.dto.response;

import com.babylion.team1.domain.feedback.entity.Feedback;

public record FeedbackExample(String before, String after) {

    static FeedbackExample from(Feedback feedback) {
        return new FeedbackExample(feedback.getExampleBefore(), feedback.getExampleAfter());
    }
}
