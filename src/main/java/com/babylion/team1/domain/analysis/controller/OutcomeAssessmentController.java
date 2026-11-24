package com.babylion.team1.domain.analysis.controller;

import com.babylion.team1.domain.analysis.dto.OutcomeAssessmentRequest;
import com.babylion.team1.domain.analysis.dto.OutcomeAssessmentResponse;
import com.babylion.team1.domain.analysis.service.OutcomeAssessmentService;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OutcomeAssessmentController {
    private final OutcomeAssessmentService service;

    @PutMapping("/api/v1/analysis-items/{itemId}/outcome")
    public ApiResponse<OutcomeAssessmentResponse> save(@PathVariable Long itemId,
            @AuthenticationPrincipal Long userId, @RequestBody OutcomeAssessmentRequest request) {
        return ApiResponse.success("S200", "결과 확인 저장 성공", service.save(itemId, userId, request));
    }
}
