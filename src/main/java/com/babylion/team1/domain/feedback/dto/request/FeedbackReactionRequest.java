package com.babylion.team1.domain.feedback.dto.request;

import com.babylion.team1.domain.feedback.entity.ReactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedbackReactionRequest(
        @NotNull @Pattern(regexp = "HELPFUL|NOT_APPLICABLE") String reaction,
        @Size(max = 500) String reason
) {
    public ReactionType type() {
        return ReactionType.valueOf(reaction);
    }
}
