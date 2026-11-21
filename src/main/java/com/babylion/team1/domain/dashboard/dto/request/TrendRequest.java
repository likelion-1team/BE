package com.babylion.team1.domain.dashboard.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 추이 조회 조건. metric만 필수다.
 * metric은 서비스에서 TrendMetric으로 바꾸며, 없거나 잘못되면 400 INVALID_REQUEST.
 * 나머지는 KPI 카드와 같다. from·to를 비우면 team_settings의 대상 기간을 쓴다(양 끝 포함, KST).
 * 날짜·unit·taskTypes 형식이 잘못되면 바인딩 단계에서 400 INVALID_REQUEST로 응답한다.
 */
public record TrendRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {
                "tokensPerSuccess", "reworkRate", "successRate", "costPerSuccess", "humanTime", "conversationCount"})
        String metric,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        KpiUnit unit,
        List<Long> taskTypes,
        List<String> models
) {

    public TrendRequest {
        unit = unit != null ? unit : KpiUnit.DAY;
        taskTypes = taskTypes != null
                ? taskTypes.stream().filter(id -> id != null).distinct().toList()
                : List.of();
        models = models != null
                ? models.stream().filter(model -> model != null).map(String::trim)
                        .filter(model -> !model.isEmpty()).distinct().toList()
                : List.of();
    }
}
