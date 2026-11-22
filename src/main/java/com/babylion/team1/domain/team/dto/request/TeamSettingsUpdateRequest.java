package com.babylion.team1.domain.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record TeamSettingsUpdateRequest(
        @NotNull @Size(min = 1, max = 1) @Valid List<@NotNull TaskType> taskTypes,
        @NotBlank @Schema(format = "date", example = "2026-11-01") String targetFrom,
        @NotBlank @Schema(format = "date", example = "2026-11-30") String targetTo,
        @NotBlank @Schema(format = "date", example = "2026-11-15") String onboardingStartDate,
        @Positive @Max(Integer.MAX_VALUE) @Digits(integer = 10, fraction = 0)
        @Schema(type = "integer", format = "int32", example = "120") BigDecimal targetCount
) {

    public record TaskType(
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Size(max = 500) String successDefinition
    ) {
    }
}
