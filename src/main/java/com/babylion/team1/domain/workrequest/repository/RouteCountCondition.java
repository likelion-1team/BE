package com.babylion.team1.domain.workrequest.repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * route_type별 건수 집계 조건.
 * projectIds는 반드시 비어 있지 않아야 하고, 나머지 목록은 비어 있으면 해당 필터를 적용하지 않는다.
 * 기간은 [createdFrom, createdTo) 반열린 구간이다.
 */
public record RouteCountCondition(
		List<Long> projectIds,
		LocalDateTime createdFrom,
		LocalDateTime createdTo,
		List<String> taskTypes,
		List<Long> policyVersionIds,
		List<String> modelVersions
) {
}
