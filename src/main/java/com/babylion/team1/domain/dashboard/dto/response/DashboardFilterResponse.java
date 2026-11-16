package com.babylion.team1.domain.dashboard.dto.response;

import java.time.LocalDate;
import java.util.List;

/** 대시보드 상단 필터 옵션 조회 응답 */
public record DashboardFilterResponse(
		DateRange dateRange,
		List<ProjectOption> projects,
		List<String> taskTypes,
		List<PolicyVersionOption> policyVersions,
		List<String> modelVersions
) {

	/** Date Range Picker 유효 기간 (업무 요청이 없으면 두 값 모두 null) */
	public record DateRange(LocalDate minDate, LocalDate maxDate) {
	}

	/** 접근 가능한 프로젝트 */
	public record ProjectOption(Long projectId, String projectName) {
	}

	/** 접근 가능한 정책 버전 + 해당 버전을 사용하는 프로젝트 ID 목록 */
	public record PolicyVersionOption(
			Long versionId,
			String versionName,
			Long policyId,
			String taskType,
			List<Long> projectIds
	) {
	}
}
