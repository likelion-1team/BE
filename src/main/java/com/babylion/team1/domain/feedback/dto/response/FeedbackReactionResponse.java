package com.babylion.team1.domain.feedback.dto.response;

import com.babylion.team1.domain.feedback.entity.FeedbackReaction;
import com.babylion.team1.domain.feedback.entity.ReactionType;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record FeedbackReactionResponse(ReactionType reaction, String updatedAt) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static FeedbackReactionResponse from(FeedbackReaction reaction) {
        String updatedAt = reaction.getUpdatedAt().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(KST).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new FeedbackReactionResponse(reaction.getReaction(), updatedAt);
    }
}
