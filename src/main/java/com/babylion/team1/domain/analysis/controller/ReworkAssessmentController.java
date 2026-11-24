package com.babylion.team1.domain.analysis.controller;

import com.babylion.team1.domain.analysis.dto.ReworkAssessmentRequest;
import com.babylion.team1.domain.analysis.dto.ReworkAssessmentResponse;
import com.babylion.team1.domain.analysis.service.ReworkAssessmentService;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReworkAssessmentController {
    private final ReworkAssessmentService service;

    @PutMapping("/api/v1/analysis-items/{itemId}/rework-assessment")
    public ApiResponse<ReworkAssessmentResponse> save(@PathVariable Long itemId,
            @AuthenticationPrincipal Long userId, @RequestBody ReworkAssessmentRequest request) {
        return ApiResponse.success("S200", "재작업 확인 저장 성공", service.save(itemId, userId, request));
    }
}
