package com.babylion.team1.domain.feedback.dto.response;

import java.time.OffsetDateTime;

public record GuideAddResponse(boolean isGuide, OffsetDateTime guideAddedAt) {
}
