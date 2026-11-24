package com.babylion.team1.domain.analysis.controller;

import com.babylion.team1.domain.analysis.dto.AnalysisStatusResponse;
import com.babylion.team1.domain.analysis.service.AnalysisQueryService;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analyses/{analysisId}")
public class AnalysisQueryController {
    private final AnalysisQueryService queryService;

    @GetMapping
    public ApiResponse<AnalysisStatusResponse> getStatus(
            @PathVariable Long analysisId, @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "조회 성공", queryService.getStatus(analysisId, userId));
    }
}
