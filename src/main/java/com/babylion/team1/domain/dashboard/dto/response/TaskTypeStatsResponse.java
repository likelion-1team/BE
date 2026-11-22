package com.babylion.team1.domain.dashboard.dto.response;

import java.util.List;

/**
 * 교육 필요 업무(팀장 전용). 업무 유형별 집계를 재작업률 높은 순으로 담는다.
 * 기간 안에 대화가 있는 업무 유형만 들어가며, 데이터가 없으면 빈 목록이다.
 */
public record TaskTypeStatsResponse(
        List<TaskTypeStatsItem> items
) {
}
