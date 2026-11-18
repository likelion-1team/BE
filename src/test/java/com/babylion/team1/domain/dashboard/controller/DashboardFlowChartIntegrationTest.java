package com.babylion.team1.domain.dashboard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.company.repository.CompanyRepository;
import com.babylion.team1.domain.project.entity.Project;
import com.babylion.team1.domain.project.repository.ProjectRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.entity.UserProject;
import com.babylion.team1.domain.user.repository.UserProjectRepository;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.domain.workrequest.entity.AiExecutionLog;
import com.babylion.team1.domain.workrequest.entity.ExecutionType;
import com.babylion.team1.domain.workrequest.entity.ProcessingMethod;
import com.babylion.team1.domain.workrequest.entity.RouteType;
import com.babylion.team1.domain.workrequest.entity.WorkRequest;
import com.babylion.team1.domain.workrequest.entity.WorkRequestStatus;
import com.babylion.team1.domain.workrequest.repository.AiExecutionLogRepository;
import com.babylion.team1.domain.workrequest.repository.WorkRequestRepository;
import com.babylion.team1.global.auth.JwtTokens;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/v1/dashboard/flow-chart 통합 테스트.
 * 실제 DB(docker compose)를 사용하며, 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardFlowChartIntegrationTest {

	private static final String URL = "/api/v1/dashboard/flow-chart";

	/** JWT_SECRET_BASE64가 없는 테스트 환경에서도 토큰을 발급·검증할 수 있도록 테스트 전용 임의 키를 넣는다. */
	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		String secret = Base64.getEncoder().encodeToString(key);
		registry.add("jwt.secret", () -> secret);
	}

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtTokens jwtTokens;
	@Autowired
	private CompanyRepository companyRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private UserProjectRepository userProjectRepository;
	@Autowired
	private WorkRequestRepository workRequestRepository;
	@Autowired
	private AiExecutionLogRepository aiExecutionLogRepository;

	private User user;
	private Project project;
	private Project otherProject;
	private String today;

	@BeforeEach
	void setUp() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Company company = companyRepository.save(
				Company.builder().companyCode("FLOW-" + suffix).companyName("흐름 테스트").build());
		user = userRepository.save(User.builder()
				.company(company)
				.employeeNumber("E-" + suffix)
				.passwordHash("hash")
				.userName("테스터")
				.roleType(RoleType.USER)
				.failedLoginCount(0)
				.build());
		project = projectRepository.save(
				Project.builder().company(company).projectCode("P-" + suffix).projectName("접근 가능").build());
		otherProject = projectRepository.save(
				Project.builder().company(company).projectCode("Q-" + suffix).projectName("접근 불가").build());
		userProjectRepository.save(UserProject.builder().user(user).project(project).build());

		// 규칙 확정 3, ERP 직결 4(gpt-4 2건), LLM 2(claude 1건), 추가 질문 1, 검토 0
		saveRequests(RouteType.RULE, 3, null);
		saveRequests(RouteType.AI_ERP_DIRECT, 2, "gpt-4");
		saveRequests(RouteType.AI_ERP_DIRECT, 2, null);
		saveRequests(RouteType.LLM, 1, "claude-3");
		saveRequests(RouteType.LLM, 1, null);
		saveRequests(RouteType.ADDITIONAL_QUESTION, 1, null);
		// 접근 권한이 없는 프로젝트 데이터는 집계되지 않아야 한다.
		workRequestRepository.save(workRequest(otherProject, RouteType.LLM));

		today = LocalDate.now().toString();
	}

	@Test
	@DisplayName("접근 가능한 프로젝트 데이터로 흐름과 부모 노드 대비 비율을 응답한다")
	void getFlowChart() throws Exception {
		mockMvc.perform(authorized(get(URL).param("startDate", today).param("endDate", today)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("S200"))
				.andExpect(jsonPath("$.message").value("경로 흐름 차트 데이터 조회가 성공적으로 완료되었습니다."))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.data.totalCount").value(10))
				.andExpect(jsonPath("$.data.flows.length()").value(6))
				.andExpect(jsonPath("$.data.flows[0].source.code").value("RECEIPT"))
				.andExpect(jsonPath("$.data.flows[0].source.label").value("접수"))
				.andExpect(jsonPath("$.data.flows[0].target.code").value("RULE_CONFIRM"))
				.andExpect(jsonPath("$.data.flows[0].trafficCount").value(3))
				.andExpect(jsonPath("$.data.flows[0].ratio").value(30.0))
				.andExpect(jsonPath("$.data.flows[1].target.code").value("DECISION_AI"))
				.andExpect(jsonPath("$.data.flows[1].trafficCount").value(7))
				.andExpect(jsonPath("$.data.flows[1].ratio").value(70.0))
				.andExpect(jsonPath("$.data.flows[2].source.code").value("DECISION_AI"))
				.andExpect(jsonPath("$.data.flows[2].target.code").value("ERP_DIRECT"))
				.andExpect(jsonPath("$.data.flows[2].trafficCount").value(4))
				.andExpect(jsonPath("$.data.flows[2].ratio").value(57.1))
				.andExpect(jsonPath("$.data.flows[3].target.code").value("LLM"))
				.andExpect(jsonPath("$.data.flows[3].ratio").value(28.6))
				.andExpect(jsonPath("$.data.flows[4].target.code").value("ADD_QUESTION"))
				.andExpect(jsonPath("$.data.flows[4].ratio").value(14.3))
				.andExpect(jsonPath("$.data.flows[5].target.code").value("REVIEW"))
				.andExpect(jsonPath("$.data.flows[5].target.label").value("담당자 검토"))
				.andExpect(jsonPath("$.data.flows[5].trafficCount").value(0))
				.andExpect(jsonPath("$.data.flows[5].ratio").value(0.0));
	}

	@Test
	@DisplayName("modelVersions 필터 적용 시 규칙 확정 건이 빠지고 접수 → 판단형 AI가 100.0%가 된다")
	void modelVersionFilter() throws Exception {
		mockMvc.perform(authorized(get(URL)
						.param("startDate", today).param("endDate", today)
						.param("modelVersions", "gpt-4,claude-3")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(3))
				.andExpect(jsonPath("$.data.flows[0].trafficCount").value(0))
				.andExpect(jsonPath("$.data.flows[0].ratio").value(0.0))
				.andExpect(jsonPath("$.data.flows[1].trafficCount").value(3))
				.andExpect(jsonPath("$.data.flows[1].ratio").value(100.0))
				.andExpect(jsonPath("$.data.flows[2].trafficCount").value(2))
				.andExpect(jsonPath("$.data.flows[2].ratio").value(66.7))
				.andExpect(jsonPath("$.data.flows[3].trafficCount").value(1))
				.andExpect(jsonPath("$.data.flows[3].ratio").value(33.3));
	}

	@Test
	@DisplayName("기간에 해당하는 데이터가 없으면 모든 흐름을 0건으로 응답한다")
	void emptyPeriod() throws Exception {
		mockMvc.perform(authorized(get(URL).param("startDate", "2000-01-01").param("endDate", "2000-01-31")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(0))
				.andExpect(jsonPath("$.data.flows.length()").value(6))
				.andExpect(jsonPath("$.data.flows[1].ratio").value(0.0));
	}

	@Test
	@DisplayName("Access Token이 없으면 401")
	void unauthorized() throws Exception {
		mockMvc.perform(get(URL).param("startDate", today).param("endDate", today))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get(URL).param("startDate", today).param("endDate", today)
						.header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("User_Project 매핑이 없는 프로젝트를 요청하면 403")
	void forbiddenProject() throws Exception {
		mockMvc.perform(authorized(get(URL).param("startDate", today).param("endDate", today)
						.param("projects", project.getId() + "," + otherProject.getId())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROJECT_ACCESS_DENIED"));
	}

	@Test
	@DisplayName("필수 날짜 누락·형식 오류·역전 기간·숫자가 아닌 ID 목록은 400")
	void badRequests() throws Exception {
		mockMvc.perform(authorized(get(URL).param("startDate", today)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
		mockMvc.perform(authorized(get(URL).param("startDate", "2026/11/01").param("endDate", today)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TYPE"));
		mockMvc.perform(authorized(get(URL).param("startDate", "2026-11-09").param("endDate", "2026-11-01")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
		mockMvc.perform(authorized(get(URL).param("startDate", today).param("endDate", today)
						.param("projects", "1,abc")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TYPE"));
		mockMvc.perform(authorized(get(URL).param("startDate", today).param("endDate", today)
						.param("policyVersions", "x")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TYPE"));
	}

	private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder) {
		return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokens.issue(user.getId()).accessToken());
	}

	private void saveRequests(RouteType routeType, int count, String modelVersion) {
		for (int i = 0; i < count; i++) {
			WorkRequest request = workRequestRepository.save(workRequest(project, routeType));
			if (modelVersion != null) {
				aiExecutionLogRepository.save(AiExecutionLog.builder()
						.workRequest(request)
						.executionType(ExecutionType.AI_JUDGMENT)
						.modelVersion(modelVersion)
						.build());
			}
		}
	}

	private WorkRequest workRequest(Project target, RouteType routeType) {
		return WorkRequest.builder()
				.user(user)
				.project(target)
				.routeType(routeType)
				.taskType("비용 정산")
				.processingMethod(ProcessingMethod.HYBRID)
				.status(WorkRequestStatus.COMPLETED)
				.reworkCount(0)
				.build();
	}
}
