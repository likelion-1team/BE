package com.babylion.team1.domain.workrequest.repository;

import java.util.List;

/** 조건이 선택적으로 붙는 동적 쿼리 (구현: WorkRequestRepositoryImpl) */
public interface WorkRequestRepositoryCustom {

	/**
	 * 조건에 맞는 업무 요청을 route_type별로 센다. route_type이 없는 건은 제외한다.
	 * modelVersions가 있으면 해당 모델 버전의 AI 실행 로그가 있는 건만 세고, 규칙 확정(RULE) 건은 제외한다.
	 */
	List<RouteTypeCount> countByRouteType(RouteCountCondition condition);
}
