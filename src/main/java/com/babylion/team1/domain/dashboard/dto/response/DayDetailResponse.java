package com.babylion.team1.domain.dashboard.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 차트에서 클릭한 날짜의 상세.
 * conversations: 그날 요청자 본인 대화(startedAt 오름차순). 팀원·팀장 모두 본인 것만 담긴다.
 * taskTypes: 그날 팀 업무 유형별 건수·지표(업무 유형 id 순). 팀장에게만 내려가고 팀원이면 null.
 * 대화가 없는 날은 빈 목록이다.
 */
public record DayDetailResponse(
        LocalDate date,
        List<DayConversationItem> conversations,
        List<DayTaskTypeItem> taskTypes
) {

    public static DayDetailResponse forMember(LocalDate date, List<DayConversationItem> conversations) {
        return new DayDetailResponse(date, conversations, null);
    }

    public static DayDetailResponse forLeader(LocalDate date, List<DayConversationItem> ownConversations,
            List<DayTaskTypeItem> taskTypes) {
        return new DayDetailResponse(date, ownConversations, taskTypes);
    }
}
