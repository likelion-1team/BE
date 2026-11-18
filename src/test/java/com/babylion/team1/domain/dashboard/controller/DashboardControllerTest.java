package com.babylion.team1.domain.dashboard.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse;
import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse.DateRange;
import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse.PolicyVersionOption;
import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse.ProjectOption;
import com.babylion.team1.domain.dashboard.service.DashboardFilterService;
import com.babylion.team1.domain.dashboard.service.DashboardFlowChartService;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DashboardController 웹 계층 테스트.
 * 서비스는 Mock으로 대체하므로 DB 없이 실행된다. (GlobalExceptionHandler는 @WebMvcTest가 함께 띄운다)
 */
@WebMvcTest(controllers = DashboardController.class)
class DashboardControllerTest {

	private static final String URL = "/api/v1/dashboard/filters";
	private static final String USER_HEADER = "X-User-Id";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DashboardFilterService dashboardFilterService;

	@MockitoBean
	private DashboardFlowChartService dashboardFlowChartService;

	/** BearerAuthenticationConfig(WebMvcConfigurer)가 의존하므로 컨텍스트 로딩용으로 등록한다. */
	@MockitoBean
	private UserRepository userRepository;

	@Test
	@DisplayName("필터 옵션 조회 성공 시 S200과 명세서 형태의 data를 응답한다")
	void getFilterOptions() throws Exception {
		DashboardFilterResponse response = new DashboardFilterResponse(
				new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 11, 9)),
				List.of(new ProjectOption(1L, "24년 하반기 비용 효율화 프로젝트"),
						new ProjectOption(2L, "CS 자동화 프로젝트")),
				List.of("비용 정산", "고객 응대", "문서 분류"),
				List.of(new PolicyVersionOption(101L, "비용 정산 자동화 v1.0", 1L, "비용 정산", List.of(1L, 2L))),
				List.of("gpt-4", "claude-3"));
		given(dashboardFilterService.getFilterOptions(1L)).willReturn(response);

		mockMvc.perform(get(URL).header(USER_HEADER, 1L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("S200"))
				.andExpect(jsonPath("$.message").value("대시보드 필터 옵션 조회가 성공적으로 완료되었습니다."))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.data.dateRange.minDate").value("2024-01-01"))
				.andExpect(jsonPath("$.data.dateRange.maxDate").value("2026-11-09"))
				.andExpect(jsonPath("$.data.projects[0].projectId").value(1))
				.andExpect(jsonPath("$.data.projects[0].projectName").value("24년 하반기 비용 효율화 프로젝트"))
				.andExpect(jsonPath("$.data.taskTypes.length()").value(3))
				.andExpect(jsonPath("$.data.policyVersions[0].versionId").value(101))
				.andExpect(jsonPath("$.data.policyVersions[0].versionName").value("비용 정산 자동화 v1.0"))
				.andExpect(jsonPath("$.data.policyVersions[0].policyId").value(1))
				.andExpect(jsonPath("$.data.policyVersions[0].taskType").value("비용 정산"))
				.andExpect(jsonPath("$.data.policyVersions[0].projectIds[0]").value(1))
				.andExpect(jsonPath("$.data.policyVersions[0].projectIds[1]").value(2))
				.andExpect(jsonPath("$.data.modelVersions[0]").value("gpt-4"));
	}

	@Test
	@DisplayName("사용자 헤더가 없으면 401 UNAUTHORIZED로 응답한다")
	void missingUserHeader() throws Exception {
		given(dashboardFilterService.getFilterOptions(isNull()))
				.willThrow(new CustomException(ErrorCode.UNAUTHORIZED));

		mockMvc.perform(get(URL))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("접근 가능한 프로젝트가 없으면 403 NO_ACCESSIBLE_PROJECT로 응답한다")
	void noAccessibleProject() throws Exception {
		given(dashboardFilterService.getFilterOptions(1L))
				.willThrow(new CustomException(ErrorCode.NO_ACCESSIBLE_PROJECT));

		mockMvc.perform(get(URL).header(USER_HEADER, 1L))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NO_ACCESSIBLE_PROJECT"))
				.andExpect(jsonPath("$.message").value(ErrorCode.NO_ACCESSIBLE_PROJECT.getMessage()));
	}

	@Test
	@DisplayName("서비스에서 예상치 못한 오류가 나면 500 INTERNAL_SERVER_ERROR로 응답한다")
	void internalServerError() throws Exception {
		given(dashboardFilterService.getFilterOptions(1L))
				.willThrow(new IllegalStateException("DB 연결 실패"));

		mockMvc.perform(get(URL).header(USER_HEADER, 1L))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
	}

	@Test
	@DisplayName("사용자 헤더가 숫자가 아니면 400 INVALID_TYPE으로 응답하고 서비스를 호출하지 않는다")
	void invalidUserHeaderType() throws Exception {
		mockMvc.perform(get(URL).header(USER_HEADER, "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TYPE"));

		verifyNoInteractions(dashboardFilterService);
	}
}
