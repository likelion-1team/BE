package com.babylion.team1.domain.dashboard.controller;

import com.babylion.team1.domain.dashboard.dto.request.DecisionRecordRequest;
import com.babylion.team1.domain.dashboard.dto.request.FlowChartRequest;
import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse;
import com.babylion.team1.domain.dashboard.dto.response.DecisionRecordResponse;
import com.babylion.team1.domain.dashboard.dto.response.FlowChartResponse;
import com.babylion.team1.domain.dashboard.service.DashboardDecisionService;
import com.babylion.team1.domain.dashboard.service.DashboardFilterService;
import com.babylion.team1.domain.dashboard.service.DashboardFlowChartService;
import com.babylion.team1.global.auth.AuthenticatedUserId;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "AI 성과 대시보드 API")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardFilterService dashboardFilterService;
	private final DashboardFlowChartService dashboardFlowChartService;
	private final DashboardDecisionService dashboardDecisionService;

	@Operation(summary = "대시보드 필터 옵션 조회",
			description = "기간, 프로젝트, 업무 유형, 정책 버전, 모델 버전 필터 옵션을 조회합니다. "
					+ "사용자가 접근 권한을 가진 프로젝트 범위의 데이터만 반환합니다.")
	@GetMapping("/filters")
	public ResponseEntity<ApiResponse<DashboardFilterResponse>> getFilterOptions(
			// TODO(auth): 인증 작업이 완료되면 토큰에서 꺼낸 사용자 ID로 교체한다. (임시 헤더)
			@Parameter(description = "임시: 로그인 사용자 ID (인증 연동 전까지 사용)")
			@RequestHeader(value = "X-User-Id", required = false) Long userId) {
		DashboardFilterResponse response = dashboardFilterService.getFilterOptions(userId);
		return ResponseEntity.ok(
				ApiResponse.success("S200", "대시보드 필터 옵션 조회가 성공적으로 완료되었습니다.", response));
	}

	@Operation(summary = "경로 흐름 차트 조회",
			description = "접수 → 규칙 확정/판단형 AI → 최종 처리 단계까지의 트래픽 건수와 분기 비율(부모 노드 대비 %)을 조회합니다. "
					+ "modelVersions 필터를 적용하면 AI를 경유하지 않은 규칙 확정 건은 집계에서 제외됩니다.")
	@GetMapping("/flow-chart")
	public ResponseEntity<ApiResponse<FlowChartResponse>> getFlowChart(
			@Parameter(hidden = true) @AuthenticatedUserId Long userId,
			@Parameter(description = "조회 시작일 (YYYY-MM-DD)", example = "2026-11-01")
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@Parameter(description = "조회 종료일 (YYYY-MM-DD)", example = "2026-11-09")
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@Parameter(description = "프로젝트 ID 목록 (콤마 구분 또는 키 반복)")
			@RequestParam(required = false) List<Long> projects,
			@Parameter(description = "업무 유형 목록")
			@RequestParam(required = false) List<String> taskTypes,
			@Parameter(description = "정책 버전 ID 목록")
			@RequestParam(required = false) List<Long> policyVersions,
			@Parameter(description = "모델 버전 목록 (적용 시 AI 미경유 건 제외)")
			@RequestParam(required = false) List<String> modelVersions) {
		FlowChartRequest request = new FlowChartRequest(
				startDate, endDate, projects, taskTypes, policyVersions, modelVersions);
		FlowChartResponse response = dashboardFlowChartService.getFlowChart(userId, request);
		return ResponseEntity.ok(
				ApiResponse.success("S200", "경로 흐름 차트 데이터 조회가 성공적으로 완료되었습니다.", response));
	}

	@Operation(summary = "대시보드 결과 기록 저장",
			description = "책임자(APPROVER)가 AI 활용 판단 후보(시스템 제안)에 내린 최종 결정을 기록합니다. "
					+ "Policy_Recommendation의 상태·결정 정보를 갱신하고 Audit_Log에 같은 트랜잭션으로 남깁니다. "
					+ "시스템 제안(next_action)과 다른 결정이면 reason이 필수이며, 이미 결정된 추천이면 409를 반환합니다.")
	@PostMapping("/decisions")
	public ResponseEntity<ApiResponse<DecisionRecordResponse>> recordDecision(
			@Parameter(hidden = true) @AuthenticatedUserId Long userId,
			@Valid @RequestBody DecisionRecordRequest request) {
		DecisionRecordResponse response = dashboardDecisionService.recordDecision(userId, request);
		return ResponseEntity.ok(
				ApiResponse.success("S200", "의사결정이 성공적으로 기록되었습니다.", response));
	}
}
