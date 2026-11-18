package com.babylion.team1.domain.dashboard.dto.request;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 경로 흐름 차트 조회 조건.
 * 목록 필터는 null·공백·중복을 정리해서 보관하며, 빈 목록은 "필터 미적용"을 뜻한다.
 */
public record FlowChartRequest(
		LocalDate startDate,
		LocalDate endDate,
		List<Long> projects,
		List<String> taskTypes,
		List<Long> policyVersions,
		List<String> modelVersions
) {

	public FlowChartRequest {
		projects = cleanIds(projects);
		taskTypes = cleanStrings(taskTypes);
		policyVersions = cleanIds(policyVersions);
		modelVersions = cleanStrings(modelVersions);
	}

	private static List<Long> cleanIds(List<Long> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream().filter(Objects::nonNull).distinct().toList();
	}

	private static List<String> cleanStrings(List<String> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.distinct()
				.toList();
	}
}
