package com.babylion.team1.domain.dashboard.dto;

import java.util.List;

/**
 * 집계 조건. userId가 null이면 팀 전체(팀장), 값이 있으면 그 사람 데이터만(팀원) 집계한다.
 * taskTypeIds·models가 비어 있으면 필터를 걸지 않는다.
 */
public record KpiCondition(
        Long teamId,
        Long userId,
        List<Long> taskTypeIds,
        List<String> models
) {
}
