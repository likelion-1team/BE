package com.babylion.team1.domain.dashboard.controller;

import com.babylion.team1.domain.dashboard.dto.request.KpiRequest;
import com.babylion.team1.domain.dashboard.dto.response.KpiResponse;
import com.babylion.team1.domain.dashboard.service.DashboardService;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
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

    /** principal은 JwtAuthenticationFilter가 넣어 둔 userId(Long)이다. */
    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<KpiResponse>> getKpis(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @ModelAttribute KpiRequest request) {
        KpiResponse response = dashboardService.getKpis(teamId, userId, request);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }
}
