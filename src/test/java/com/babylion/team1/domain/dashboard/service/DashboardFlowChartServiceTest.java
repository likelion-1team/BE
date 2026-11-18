package com.babylion.team1.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.dashboard.dto.request.FlowChartRequest;
import com.babylion.team1.domain.dashboard.dto.response.FlowChartResponse;
import com.babylion.team1.domain.project.entity.Project;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserProjectRepository;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.domain.workrequest.entity.RouteType;
import com.babylion.team1.domain.workrequest.repository.RouteCountCondition;
import com.babylion.team1.domain.workrequest.repository.RouteTypeCount;
import com.babylion.team1.domain.workrequest.repository.WorkRequestRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** DashboardFlowChartService 단위 테스트 (Repository Mock, DB 불필요) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardFlowChartServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long COMPANY_ID = 10L;
	private static final LocalDate START = LocalDate.of(2026, 11, 1);
	private static final LocalDate END = LocalDate.of(2026, 11, 9);

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserProjectRepository userProjectRepository;
	@Mock
	private WorkRequestRepository workRequestRepository;

	@InjectMocks
	private DashboardFlowChartService service;

	@BeforeEach
	void setUp() {
		Company company = Company.builder().companyCode("C").companyName("회사").build();
		ReflectionTestUtils.setField(company, "id", COMPANY_ID);
		User user = User.builder().company(company).build();
		ReflectionTestUtils.setField(user, "id", USER_ID);
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
		given(userProjectRepository.findAccessibleProjects(USER_ID, COMPANY_ID))
				.willReturn(List.of(project(1L, company), project(2L, company)));
	}

	@Test
	@DisplayName("route_type별 건수로 2단계 흐름과 부모 노드 대비 비율을 만든다")
	void buildsFlows() {
		given(workRequestRepository.countByRouteType(any())).willReturn(List.of(
				new RouteTypeCount(RouteType.RULE, 300),
				new RouteTypeCount(RouteType.AI_ERP_DIRECT, 350),
				new RouteTypeCount(RouteType.LLM, 210),
				new RouteTypeCount(RouteType.ADDITIONAL_QUESTION, 70),
				new RouteTypeCount(RouteType.MANUAL_REVIEW, 70)));

		FlowChartResponse response = service.getFlowChart(USER_ID, request(null, null));

		assertThat(response.totalCount()).isEqualTo(1000);
		assertThat(response.flows())
				.extracting(f -> f.source().code(), f -> f.target().code(), f -> f.target().label(),
						FlowChartResponse.Flow::trafficCount, FlowChartResponse.Flow::ratio)
				.containsExactly(
						tuple("RECEIPT", "RULE_CONFIRM", "규칙 확정", 300L, 30.0),
						tuple("RECEIPT", "DECISION_AI", "판단형 AI", 700L, 70.0),
						tuple("DECISION_AI", "ERP_DIRECT", "ERP 직결", 350L, 50.0),
						tuple("DECISION_AI", "LLM", "LLM", 210L, 30.0),
						tuple("DECISION_AI", "ADD_QUESTION", "추가 질문", 70L, 10.0),
						tuple("DECISION_AI", "REVIEW", "담당자 검토", 70L, 10.0));
		assertThat(response.flows().get(0).source().label()).isEqualTo("접수");
	}

	@Test
	@DisplayName("비율은 소수 첫째 자리에서 반올림하고, 데이터가 없으면 모든 흐름이 0건/0.0%다")
	void roundsRatioAndHandlesEmpty() {
		given(workRequestRepository.countByRouteType(any())).willReturn(List.of(
				new RouteTypeCount(RouteType.RULE, 1),
				new RouteTypeCount(RouteType.LLM, 2)));

		FlowChartResponse response = service.getFlowChart(USER_ID, request(null, null));
		assertThat(response.flows().get(0).ratio()).isEqualTo(33.3);
		assertThat(response.flows().get(1).ratio()).isEqualTo(66.7);
		assertThat(response.flows().get(2).ratio()).isEqualTo(0.0);
		assertThat(response.flows().get(3).ratio()).isEqualTo(100.0);

		given(workRequestRepository.countByRouteType(any())).willReturn(List.of());
		FlowChartResponse empty = service.getFlowChart(USER_ID, request(null, null));
		assertThat(empty.totalCount()).isZero();
		assertThat(empty.flows()).hasSize(6).allSatisfy(flow -> {
			assertThat(flow.trafficCount()).isZero();
			assertThat(flow.ratio()).isZero();
		});
	}

	@Test
	@DisplayName("modelVersions 필터 결과에 규칙 확정 건이 없으면 접수 → 판단형 AI 비율은 100.0%다")
	void modelVersionFilterMakesDecisionAiFull() {
		given(workRequestRepository.countByRouteType(any())).willReturn(List.of(
				new RouteTypeCount(RouteType.AI_ERP_DIRECT, 3),
				new RouteTypeCount(RouteType.LLM, 1)));

		FlowChartResponse response = service.getFlowChart(USER_ID,
				new FlowChartRequest(START, END, null, null, null, List.of("gpt-4")));

		assertThat(response.totalCount()).isEqualTo(4);
		assertThat(response.flows().get(0).trafficCount()).isZero();
		assertThat(response.flows().get(1).ratio()).isEqualTo(100.0);
	}

	@Test
	@DisplayName("프로젝트 미지정 시 접근 가능한 전체 프로젝트와 [시작일 00:00, 종료일+1 00:00) 기간으로 조회한다")
	void passesCondition() {
		given(workRequestRepository.countByRouteType(any())).willReturn(List.of());

		service.getFlowChart(USER_ID, new FlowChartRequest(START, END, null,
				List.of(" 비용 정산 ", "", "비용 정산"), List.of(101L), List.of("gpt-4")));

		ArgumentCaptor<RouteCountCondition> captor = ArgumentCaptor.forClass(RouteCountCondition.class);
		verify(workRequestRepository).countByRouteType(captor.capture());
		RouteCountCondition condition = captor.getValue();
		assertThat(condition.projectIds()).containsExactlyInAnyOrder(1L, 2L);
		assertThat(condition.createdFrom()).isEqualTo(START.atStartOfDay());
		assertThat(condition.createdTo()).isEqualTo(END.plusDays(1).atStartOfDay());
		assertThat(condition.taskTypes()).containsExactly("비용 정산");
		assertThat(condition.policyVersionIds()).containsExactly(101L);
		assertThat(condition.modelVersions()).containsExactly("gpt-4");
	}

	@Test
	@DisplayName("접근 권한이 없는 프로젝트를 요청하면 403 PROJECT_ACCESS_DENIED")
	void deniesInaccessibleProject() {
		assertThatThrownBy(() -> service.getFlowChart(USER_ID, request(List.of(1L, 3L), null)))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
		verifyNoInteractions(workRequestRepository);
	}

	@Test
	@DisplayName("접근 가능한 프로젝트가 하나도 없으면 403 NO_ACCESSIBLE_PROJECT")
	void noAccessibleProject() {
		given(userProjectRepository.findAccessibleProjects(USER_ID, COMPANY_ID)).willReturn(List.of());

		assertThatThrownBy(() -> service.getFlowChart(USER_ID, request(null, null)))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.NO_ACCESSIBLE_PROJECT);
	}

	@Test
	@DisplayName("종료일이 시작일보다 과거면 400 INVALID_DATE_RANGE")
	void invalidDateRange() {
		assertThatThrownBy(() -> service.getFlowChart(USER_ID,
				new FlowChartRequest(END, START, null, null, null, null)))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_DATE_RANGE);
		verifyNoInteractions(workRequestRepository);
	}

	private FlowChartRequest request(List<Long> projects, List<String> modelVersions) {
		return new FlowChartRequest(START, END, projects, null, null, modelVersions);
	}

	private Project project(Long id, Company company) {
		Project project = Project.builder().company(company).projectCode("P" + id).projectName("프로젝트" + id).build();
		ReflectionTestUtils.setField(project, "id", id);
		return project;
	}
}
