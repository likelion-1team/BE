package com.babylion.team1.domain.dashboard.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 차트에서 클릭한 날짜의 상세.
 * 팀원: conversations(그날 본인 대화, startedAt 오름차순), taskTypes는 null.
 * 팀장: taskTypes(그날 업무 유형별 건수·지표, 업무 유형 id 순), conversations는 null.
 * 대화가 없는 날은 해당 쪽이 빈 목록이다.
 */
public record DayDetailResponse(
        LocalDate date,
        List<DayConversationItem> conversations,
        List<DayTaskTypeItem> taskTypes
) {

    public static DayDetailResponse forMember(LocalDate date, List<DayConversationItem> conversations) {
        return new DayDetailResponse(date, conversations, null);
    }

    public static DayDetailResponse forLeader(LocalDate date, List<DayTaskTypeItem> taskTypes) {
        return new DayDetailResponse(date, null, taskTypes);
    }
}
