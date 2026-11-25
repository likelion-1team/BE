package com.babylion.team1.domain.feedback.dto.response;

public record FeedbackShareResponse(boolean shared, int shareVersion, Long sharedCaseId) {
}
