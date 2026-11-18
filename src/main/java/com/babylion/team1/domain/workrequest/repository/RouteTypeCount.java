package com.babylion.team1.domain.workrequest.repository;

import com.babylion.team1.domain.workrequest.entity.RouteType;

/** route_type별 업무 요청 건수 집계 결과 */
public record RouteTypeCount(RouteType routeType, long count) {
}
