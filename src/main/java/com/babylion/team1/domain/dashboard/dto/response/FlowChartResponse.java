package com.babylion.team1.domain.dashboard.dto.response;

import java.util.List;

/** 대시보드 경로 흐름 차트 조회 응답 */
public record FlowChartResponse(
		long totalCount,
		List<Flow> flows
) {

	/** source → target 흐름. ratio는 부모(source) 노드 건수 대비 %다. */
	public record Flow(
			Node source,
			Node target,
			long trafficCount,
			double ratio
	) {
	}

	/** 차트 노드 (식별용 code + UI 표시용 label) */
	public record Node(String code, String label) {
	}
}
