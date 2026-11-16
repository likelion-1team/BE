package com.babylion.team1.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse;
import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse.PolicyVersionOption;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * DashboardFilterService 단위 테스트.
 * Repository는 모두 Mock으로 대체하므로 DB 없이 실행된다.
 */
@ExtendWith(MockitoExtension.class)
class DashboardFilterServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long COMPANY_ID = 10L;

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserProjectRepository userProjectRepository;
	@Mock
	private WorkRequestRepository workRequestRepository;
	@Mock
	private AiExecutionLogRepository aiExecutionLogRepository;
	@Mock
	private ProjectPolicyMappingRepository projectPolicyMappingRepository;

	@InjectMocks
	private DashboardFilterService dashboardFilterService;

	@Test
	@DisplayName("접근 가능한 프로젝트 범위로 필터 옵션을 모두 조회한다")
	void getFilterOptions() {
		// given
		givenUserOfCompany();
		List<Long> projectIds = List.of(1L, 2L);
		// 프로젝트 조회에 토큰 사용자의 회사 ID가 함께 넘어가야 한다 (다르면 Mockito strict stubs에서 실패)
		List<Project> projects = List.of(project(1L, "비용 효율화 프로젝트"), project(2L, "CS 자동화 프로젝트"));
		given(userProjectRepository.findAccessibleProjects(USER_ID, COMPANY_ID)).willReturn(projects);
		given(workRequestRepository.findMinCreatedAt(projectIds))
				.willReturn(LocalDateTime.of(2024, 1, 1, 9, 30));
		given(workRequestRepository.findMaxCreatedAt(projectIds))
				.willReturn(LocalDateTime.of(2026, 11, 9, 23, 59));
		given(workRequestRepository.findDistinctTaskTypes(projectIds))
				.willReturn(List.of("고객 응대", "비용 정산"));
		given(aiExecutionLogRepository.findDistinctModelVersions(projectIds))
				.willReturn(List.of("claude-3", "gpt-4"));
		given(projectPolicyMappingRepository.findPolicyVersionRows(projectIds, COMPANY_ID))
				.willReturn(List.of(
						row(101L, "비용 정산 자동화 v1.0", 1L, "비용 정산", 1L),
						row(101L, "비용 정산 자동화 v1.0", 1L, "비용 정산", 2L),
						row(102L, "고객 응대 자동화 v1.0", 2L, "고객 응대", 2L)));

		// when
		DashboardFilterResponse response = dashboardFilterService.getFilterOptions(USER_ID);

		// then
		assertThat(response.dateRange().minDate()).isEqualTo(LocalDate.of(2024, 1, 1));
		assertThat(response.dateRange().maxDate()).isEqualTo(LocalDate.of(2026, 11, 9));
		assertThat(response.projects())
				.extracting("projectId", "projectName")
				.containsExactly(
						tuple(1L, "비용 효율화 프로젝트"),
						tuple(2L, "CS 자동화 프로젝트"));
		assertThat(response.taskTypes()).containsExactly("고객 응대", "비용 정산");
		assertThat(response.modelVersions()).containsExactly("claude-3", "gpt-4");
	}

	@Test
	@DisplayName("정책 버전은 버전 단위로 묶이고, 사용하는 프로젝트 ID가 projectIds 배열로 합쳐진다")
	void groupPolicyVersionsByVersion() {
		// given
		givenUserOfCompany();
		List<Long> projectIds = List.of(1L, 2L);
		List<Project> projects = List.of(project(1L, "A"), project(2L, "B"));
		given(userProjectRepository.findAccessibleProjects(USER_ID, COMPANY_ID)).willReturn(projects);
		given(projectPolicyMappingRepository.findPolicyVersionRows(projectIds, COMPANY_ID))
				.willReturn(List.of(
						row(101L, "v1.0", 1L, "비용 정산", 1L),
						row(101L, "v1.0", 1L, "비용 정산", 2L),
						row(102L, "v2.0", 2L, "고객 응대", 2L)));

		// when
		List<PolicyVersionOption> policyVersions =
				dashboardFilterService.getFilterOptions(USER_ID).policyVersions();

		// then
		assertThat(policyVersions).hasSize(2);

		PolicyVersionOption first = policyVersions.get(0);
		assertThat(first.versionId()).isEqualTo(101L);
		assertThat(first.versionName()).isEqualTo("v1.0");
		assertThat(first.policyId()).isEqualTo(1L);
		assertThat(first.taskType()).isEqualTo("비용 정산");
		assertThat(first.projectIds()).containsExactly(1L, 2L);

		PolicyVersionOption second = policyVersions.get(1);
		assertThat(second.versionId()).isEqualTo(102L);
		assertThat(second.projectIds()).containsExactly(2L);
	}

	@Test
	@DisplayName("업무 요청이 없으면 dateRange의 minDate와 maxDate는 null이다")
	void emptyDateRange() {
		// given
		givenUserOfCompany();
		List<Project> projects = List.of(project(1L, "A"));
		given(userProjectRepository.findAccessibleProjects(USER_ID, COMPANY_ID)).willReturn(projects);
		// 나머지 Repository 메서드는 stub하지 않아 Mockito 기본값(null, 빈 리스트)을 돌려준다.

		// when
		DashboardFilterResponse response = dashboardFilterService.getFilterOptions(USER_ID);

		// then
		assertThat(response.dateRange().minDate()).isNull();
		assertThat(response.dateRange().maxDate()).isNull();
		assertThat(response.taskTypes()).isEmpty();
		assertThat(response.modelVersions()).isEmpty();
		assertThat(response.policyVersions()).isEmpty();
	}

	@Test
	@DisplayName("사용자 ID가 없으면 UNAUTHORIZED 예외가 발생하고 아무것도 조회하지 않는다")
	void nullUserId() {
		assertThatThrownBy(() -> dashboardFilterService.getFilterOptions(null))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.UNAUTHORIZED);

		verifyNoInteractions(userRepository, userProjectRepository, workRequestRepository,
				aiExecutionLogRepository, projectPolicyMappingRepository);
	}

	@Test
	@DisplayName("존재하지 않는 사용자면 UNAUTHORIZED 예외가 발생한다")
	void userNotFound() {
		given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> dashboardFilterService.getFilterOptions(USER_ID))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.UNAUTHORIZED);

		verifyNoInteractions(userProjectRepository, workRequestRepository,
				aiExecutionLogRepository, projectPolicyMappingRepository);
	}

	@Test
	@DisplayName("접근 가능한 프로젝트가 없으면 NO_ACCESSIBLE_PROJECT 예외가 발생하고 이후 조회는 하지 않는다")
	void noAccessibleProject() {
		givenUserOfCompany();
		given(userProjectRepository.findAccessibleProjects(USER_ID, COMPANY_ID)).willReturn(List.of());

		assertThatThrownBy(() -> dashboardFilterService.getFilterOptions(USER_ID))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.NO_ACCESSIBLE_PROJECT);

		verifyNoInteractions(workRequestRepository, aiExecutionLogRepository, projectPolicyMappingRepository);
	}

	private void givenUserOfCompany() {
		Company company = mock(Company.class);
		given(company.getId()).willReturn(COMPANY_ID);
		User user = mock(User.class);
		given(user.getCompany()).willReturn(company);
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
	}

	private Project project(Long id, String name) {
		Project project = mock(Project.class);
		given(project.getId()).willReturn(id);
		given(project.getProjectName()).willReturn(name);
		return project;
	}

	private PolicyVersionProjectRow row(Long versionId, String versionName, Long policyId,
			String taskType, Long projectId) {
		return new PolicyVersionProjectRow() {
			@Override
			public Long getVersionId() {
				return versionId;
			}

			@Override
			public String getVersionName() {
				return versionName;
			}

			@Override
			public Long getPolicyId() {
				return policyId;
			}

			@Override
			public String getTaskType() {
				return taskType;
			}

			@Override
			public Long getProjectId() {
				return projectId;
			}
		};
	}
}
