package com.babylion.team1.domain.dashboard.controller;

import com.babylion.team1.domain.dashboard.dto.request.KpiRequest;
import com.babylion.team1.domain.dashboard.dto.request.OnboardingEffectRequest;
import com.babylion.team1.domain.dashboard.dto.response.KpiResponse;
import com.babylion.team1.domain.dashboard.dto.response.OnboardingEffectResponse;
import com.babylion.team1.domain.dashboard.service.DashboardService;
import com.babylion.team1.global.response.ApiResponse;
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
@RequestMapping("/api/v1/teams/{teamId}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * principal은 JwtAuthenticationFilter가 넣어 둔 userId(Long)이다.
     * @ParameterObject: Swagger에서 request를 JSON 하나가 아니라 from·to·unit·taskTypes·models 개별 쿼리 파라미터로 보여준다.
     */
    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<KpiResponse>> getKpis(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @ParameterObject @ModelAttribute KpiRequest request) {
        KpiResponse response = dashboardService.getKpis(teamId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }

    @GetMapping("/onboarding-effect")
    public ResponseEntity<ApiResponse<OnboardingEffectResponse>> getOnboardingEffect(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @ParameterObject @ModelAttribute OnboardingEffectRequest request) {
        OnboardingEffectResponse response = dashboardService.getOnboardingEffect(teamId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }
}
