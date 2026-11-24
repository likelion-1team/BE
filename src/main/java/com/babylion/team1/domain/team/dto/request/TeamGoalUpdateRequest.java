package com.babylion.team1.domain.team.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record TeamGoalUpdateRequest(
        @NotNull @PositiveOrZero @Max(Integer.MAX_VALUE) @Digits(integer = 10, fraction = 0)
        @Schema(type = "integer", format = "int32", example = "0") BigDecimal expectedVersion,
        @DecimalMin("0") @DecimalMax("1") @Schema(example = "0.8") BigDecimal successRateMin,
        @DecimalMin("0") @DecimalMax("1") @Schema(example = "0.2") BigDecimal errorReworkRateMax,
        @Positive @Max(Integer.MAX_VALUE) @Digits(integer = 10, fraction = 0)
        @Schema(type = "integer", format = "int32", example = "5000") BigDecimal tokensPerSuccessMax
) {
}
