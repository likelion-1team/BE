package com.babylion.team1.domain.team.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record TeamSettingsUpdateRequest(
        @NotNull @PositiveOrZero @Max(Integer.MAX_VALUE) @Digits(integer = 10, fraction = 0)
        @Schema(type = "integer", format = "int32", example = "0") BigDecimal expectedVersion,
        @NotNull @Valid TaskType taskType,
        @NotBlank @Schema(format = "date", example = "2026-11-01") String targetFrom,
        @NotBlank @Schema(format = "date", example = "2026-11-30") String targetTo,
        @Schema(format = "date", example = "2026-11-16", nullable = true) String onboardingStartDate,
        @Positive @Max(Integer.MAX_VALUE) @Digits(integer = 10, fraction = 0)
        @Schema(type = "integer", format = "int32", example = "120", nullable = true) BigDecimal targetCount
) {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown field: " + name);
    }

    public record TaskType(
            @NotBlank @Size(max = 200) @Schema(example = "주간 보고서 작성") String name,
            @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 500) String> successConditions,
            @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 500) String> failureConditions,
            @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 500) String> unknownConditions
    ) {

        @JsonAnySetter
        void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Unknown field: " + name);
        }
    }
}
