package com.babylion.team1.domain.feedback.dto.request;

import com.babylion.team1.domain.feedback.entity.ReactionType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedbackReactionRequest(
        @NotNull @Pattern(regexp = "HELPFUL|NOT_APPLICABLE") String reaction,
        @Size(max = 200) String reason
) {
    @AssertTrue(message = "해당 없음 반응에만 사유를 입력할 수 있습니다.")
    public boolean isReasonAllowed() {
        return reason == null || ReactionType.NOT_APPLICABLE.name().equals(reaction);
    }

    public ReactionType type() {
        return ReactionType.valueOf(reaction);
    }
}
