package com.babylion.team1.domain.dashboard.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 대시보드 필터 옵션. 팀원이면 본인 대화, 팀장이면 팀 전체 대화에 실제로 등장한 값만 담는다.
 * taskTypes: id 오름차순, models: 이름 오름차순(NULL 제외).
 * dataFrom·dataTo: 대화가 있는 첫날·마지막 날(conversation_date). 대화가 없으면 null.
 */
public record FilterOptionsResponse(
        List<TaskTypeOption> taskTypes,
        List<String> models,
        LocalDate dataFrom,
        LocalDate dataTo
) {
}
