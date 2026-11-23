package com.babylion.team1.domain.dashboard.dto.request;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * KPI 조회 조건. 모두 선택 값이다.
 * from·to를 비우면 team_settings의 대상 기간을 쓴다. 양 끝 날짜를 포함한다(KST).
 * taskTypes는 업무 유형 ID, models는 모델 이름을 쉼표로 이어서 받는다.
 * 날짜·unit·taskTypes 형식이 잘못되면 바인딩 단계에서 400 INVALID_REQUEST로 응답한다.
 * unit은 KPI 카드 값에는 영향이 없고, 차트 API와 같은 조건을 그대로 넘길 수 있도록 검증만 한다.
 * scope는 집계 범위(ME·TEAM)다. 생략하면 팀장은 TEAM, 팀원은 ME. 팀원이 TEAM을 보내면 403 LEADER_ONLY.
 */
public record KpiRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        KpiUnit unit,
        List<Long> taskTypes,
        List<String> models,
        DashboardScope scope
) {

    public KpiRequest {
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
