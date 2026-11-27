package com.babylion.team1.domain.comparison;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromptComparisonRequest(
        @NotBlank @Size(max = 6_000) String task,
        @NotBlank @Size(max = 4_000) String baselinePrompt,
        @NotBlank @Size(max = 4_000) String improvedPrompt
) {
}
