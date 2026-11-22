package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.dashboard.dto.request.CostGroupBy;
import java.util.List;

/**
 * 비용 구조(누적 막대 차트용). 팀원이면 본인 값, 팀장이면 팀 집계.
 * 비용은 추정 토큰 x 공시 단가, 검토·수습 시간 x 시간당 인건비로 계산한 추정치라 estimated는 항상 true.
 * points: 대화가 없는 날(주)도 빠짐없이 날짜 오름차순.
 */
public record CostBreakdownResponse(
        CostGroupBy groupBy,
        boolean estimated,
        List<CostPoint> points
) {

    public static CostBreakdownResponse of(CostGroupBy groupBy, List<CostPoint> points) {
        return new CostBreakdownResponse(groupBy, true, points);
    }
}
