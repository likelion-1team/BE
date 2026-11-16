package com.babylion.team1.domain.dashboard.controller;

import com.babylion.team1.domain.dashboard.dto.response.DashboardFilterResponse;
import com.babylion.team1.domain.dashboard.service.DashboardFilterService;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "AI 성과 대시보드 API")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardFilterService dashboardFilterService;

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
}
