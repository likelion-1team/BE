package com.babylion.team1.domain.feedback.controller;

import com.babylion.team1.domain.feedback.dto.response.GuideAddResponse;
import com.babylion.team1.domain.team.service.TeamService;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/feedbacks")
public class FeedbackGuideController {

    private final TeamService teamService;

    @Operation(summary = "개선 가이드 지정")
    @PutMapping("/{feedbackId}/guide")
    public ApiResponse<GuideAddResponse> addGuide(
            @PathVariable Long feedbackId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "가이드 지정 성공", teamService.addGuide(feedbackId, userId));
    }
}
