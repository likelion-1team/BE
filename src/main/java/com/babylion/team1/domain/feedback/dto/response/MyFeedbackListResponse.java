package com.babylion.team1.domain.feedback.dto.response;

import com.babylion.team1.domain.feedback.entity.ReactionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MyFeedbackListResponse(List<Day> days) {

    public record Day(LocalDate date, List<Item> feedbacks) {
    }

    public record Item(Long feedbackId, String type, String title, Integer occurrenceCount,
                       ExpectedSaving expectedSaving,
                       @JsonInclude(JsonInclude.Include.ALWAYS) ReactionType myReaction,
                       Boolean isShared) {
    }

    public record ExpectedSaving(Integer tokens, BigDecimal cost, Integer turns) {
    }
}
