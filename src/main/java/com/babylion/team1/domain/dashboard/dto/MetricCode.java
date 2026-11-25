package com.babylion.team1.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

/** 대시보드 지표(v4 명세 7.2). 요청·응답 값은 camelCase 이름이다. */
@Getter
public enum MetricCode {

    CONFIRMED_SUCCESS_RATE("confirmedSuccessRate", Unit.RATIO, ValueSource.USER_INPUT),
    OUTCOME_CONFIRMATION_RATE("outcomeConfirmationRate", Unit.RATIO, ValueSource.USER_INPUT),
    ERROR_REWORK_RATE("errorReworkRate", Unit.RATIO, ValueSource.USER_INPUT),
    REWORK_CONFIRMATION_RATE("reworkConfirmationRate", Unit.RATIO, ValueSource.USER_INPUT),
    TOKENS_PER_CONFIRMED_SUCCESS("tokensPerConfirmedSuccess", Unit.TOKENS_PER_SUCCESS, ValueSource.TOKENIZER_ESTIMATE);

    @JsonValue
    private final String value;
    private final Unit unit;
    private final ValueSource valueSource;

    MetricCode(String value, Unit unit, ValueSource valueSource) {
        this.value = value;
        this.unit = unit;
        this.valueSource = valueSource;
    }

    public static Optional<MetricCode> find(String value) {
        return Arrays.stream(values()).filter(code -> code.value.equals(value)).findFirst();
    }

    public enum Unit {
        RATIO,
        TOKENS_PER_SUCCESS
    }

    public enum ValueSource {
        USER_INPUT,
        TOKENIZER_ESTIMATE
    }
}
