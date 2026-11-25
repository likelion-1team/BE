package com.babylion.team1.domain.feedback.dto.request;

import jakarta.validation.constraints.NotNull;

public record GuideAddRequest(@NotNull Integer expectedShareVersion) {
}
