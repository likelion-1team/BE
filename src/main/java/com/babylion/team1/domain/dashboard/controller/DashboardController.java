package com.babylion.team1.domain.dashboard.controller;

import com.babylion.team1.domain.dashboard.dto.request.CostBreakdownRequest;
import com.babylion.team1.domain.dashboard.dto.request.KpiRequest;
import com.babylion.team1.domain.dashboard.dto.response.CostBreakdownResponse;
import com.babylion.team1.domain.dashboard.dto.request.OnboardingEffectRequest;
import com.babylion.team1.domain.dashboard.dto.request.TaskTypeStatsRequest;
import com.babylion.team1.domain.dashboard.dto.request.TrendRequest;
import com.babylion.team1.domain.dashboard.dto.response.TaskTypeStatsResponse;
import com.babylion.team1.domain.dashboard.dto.response.TrendResponse;
import com.babylion.team1.domain.dashboard.dto.response.DayDetailResponse;
import com.babylion.team1.domain.dashboard.dto.response.FilterOptionsResponse;
import com.babylion.team1.domain.dashboard.dto.response.KpiResponse;
import com.babylion.team1.domain.dashboard.dto.response.OnboardingEffectResponse;
import com.babylion.team1.domain.dashboard.service.DashboardService;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}")
public class DashboardController {

    private final DashboardService dashboardService;

    /** 대시보드 진입 시 필터 목록 준비. 명세상 경로가 /dashboard 밖이라 기본 경로를 /teams/{teamId}로 둔다. */
    @GetMapping("/filter-options")
    public ResponseEntity<ApiResponse<FilterOptionsResponse>> getFilterOptions(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId) {
        FilterOptionsResponse response = dashboardService.getFilterOptions(teamId, userId);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }

    /**
     * 차트에서 클릭한 날짜의 상세. 팀원은 그날 본인 대화 목록, 팀장은 그날 업무 유형별 건수·지표.
     * date는 문자열로 받아 서비스에서 파싱한다. 형식이 잘못되면 400 INVALID_REQUEST.
     */
    @GetMapping("/days/{date}")
    public ResponseEntity<ApiResponse<DayDetailResponse>> getDayDetail(
            @PathVariable Long teamId,
            @Parameter(description = "조회할 날짜(yyyy-MM-dd, KST)", schema = @Schema(type = "string", format = "date"))
            @PathVariable String date,
            @AuthenticationPrincipal Long userId) {
        DayDetailResponse response = dashboardService.getDayDetail(teamId, userId, date);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }

    /**
     * principal은 JwtAuthenticationFilter가 넣어 둔 userId(Long)이다.
     * @ParameterObject: Swagger에서 request를 JSON 하나가 아니라 from·to·unit·taskTypes·models 개별 쿼리 파라미터로 보여준다.
     */
    @GetMapping("/dashboard/kpis")
    public ResponseEntity<ApiResponse<KpiResponse>> getKpis(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @ParameterObject @ModelAttribute KpiRequest request) {
        KpiResponse response = dashboardService.getKpis(teamId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }

    @GetMapping("/dashboard/trends")
    public ResponseEntity<ApiResponse<TrendResponse>> getTrends(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @ParameterObject @ModelAttribute TrendRequest request) {
        TrendResponse response = dashboardService.getTrends(teamId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }

    @GetMapping("/dashboard/cost-breakdown")
    public ResponseEntity<ApiResponse<CostBreakdownResponse>> getCostBreakdown(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @ParameterObject @ModelAttribute CostBreakdownRequest request) {
        CostBreakdownResponse response = dashboardService.getCostBreakdown(teamId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }

    /** 교육 필요 업무(팀장 전용). 팀원이면 403 LEADER_ONLY */
    @GetMapping("/dashboard/task-types")
    public ResponseEntity<ApiResponse<TaskTypeStatsResponse>> getTaskTypeStats(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @ParameterObject @ModelAttribute TaskTypeStatsRequest request) {
        TaskTypeStatsResponse response = dashboardService.getTaskTypeStats(teamId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }

    @GetMapping("/dashboard/onboarding-effect")
    public ResponseEntity<ApiResponse<OnboardingEffectResponse>> getOnboardingEffect(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @ParameterObject @ModelAttribute OnboardingEffectRequest request) {
        OnboardingEffectResponse response = dashboardService.getOnboardingEffect(teamId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }
}
