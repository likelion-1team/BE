package com.babylion.team1.domain.feedback.entity;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedbackType {
    MISSING_CONTEXT("맥락 누락"),
    LONG_CONVERSATION("긴 대화"),
    OVERSPEC_MODEL("과한 모델");

    private final String label;

    public static String displayName(String value) {
        return matching(value).map(FeedbackType::getLabel).orElse(value);
    }

    public static String alternateValue(String value) {
        return matching(value)
                .map(type -> type.name().equalsIgnoreCase(value) ? type.label : type.name())
                .orElse(null);
    }

    private static Optional<FeedbackType> matching(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value) || type.label.equals(value))
                .findFirst();
    }
}
