package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse;
import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse.DateRange;
import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse.PolicyVersionOption;
import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse.ProjectOption;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository.PolicyVersionProjectRow;
import com.babylion.team1.domain.project.entity.Project;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserProjectRepository;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.domain.workrequest.repository.AiExecutionLogRepository;
import com.babylion.team1.domain.workrequest.repository.WorkRequestRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardFilterService {

	private final UserRepository userRepository;
	private final UserProjectRepository userProjectRepository;
	private final WorkRequestRepository workRequestRepository;
	private final AiExecutionLogRepository aiExecutionLogRepository;
	private final ProjectPolicyMappingRepository projectPolicyMappingRepository;

	public DashboardFilterResponse getFilterOptions(Long userId) {
		// 회사 ID는 요청으로 받지 않고 인증된 사용자 정보에서 꺼낸다.
		User user = findAuthenticatedUser(userId);
		Long companyId = user.getCompany().getId();

		List<Project> projects = userProjectRepository.findAccessibleProjects(userId, companyId);
		if (projects.isEmpty()) {
			throw new CustomException(ErrorCode.NO_ACCESSIBLE_PROJECT);
		}
		List<Long> projectIds = projects.stream().map(Project::getId).toList();

		DateRange dateRange = new DateRange(
				toDate(workRequestRepository.findMinCreatedAt(projectIds)),
				toDate(workRequestRepository.findMaxCreatedAt(projectIds)));

		List<ProjectOption> projectOptions = projects.stream()
				.map(project -> new ProjectOption(project.getId(), project.getProjectName()))
				.toList();

		List<String> taskTypes = workRequestRepository.findDistinctTaskTypes(projectIds);
		List<String> modelVersions = aiExecutionLogRepository.findDistinctModelVersions(projectIds);
		List<PolicyVersionOption> policyVersions = toPolicyVersionOptions(
				projectPolicyMappingRepository.findPolicyVersionRows(projectIds, companyId));

		return new DashboardFilterResponse(dateRange, projectOptions, taskTypes, policyVersions, modelVersions);
	}

	private User findAuthenticatedUser(Long userId) {
		if (userId == null) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
		return userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
	}

	/** (버전, 프로젝트) 행들을 버전 단위로 묶어 projectIds 배열을 만든다. */
	private List<PolicyVersionOption> toPolicyVersionOptions(List<PolicyVersionProjectRow> rows) {
		Map<Long, List<PolicyVersionProjectRow>> rowsByVersion = rows.stream()
				.collect(Collectors.groupingBy(PolicyVersionProjectRow::getVersionId,
						LinkedHashMap::new, Collectors.toList()));

		return rowsByVersion.values().stream()
				.map(versionRows -> {
					PolicyVersionProjectRow first = versionRows.get(0);
					List<Long> projectIds = versionRows.stream()
							.map(PolicyVersionProjectRow::getProjectId)
							.distinct()
							.toList();
					return new PolicyVersionOption(first.getVersionId(), first.getVersionName(),
							first.getPolicyId(), first.getTaskType(), projectIds);
				})
				.toList();
	}

	private LocalDate toDate(LocalDateTime dateTime) {
		return dateTime != null ? dateTime.toLocalDate() : null;
	}
}
