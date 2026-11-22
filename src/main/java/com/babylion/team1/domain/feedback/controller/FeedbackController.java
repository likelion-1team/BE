package com.babylion.team1.domain.feedback.controller;

import com.babylion.team1.domain.feedback.dto.response.FeedbackDetailResponse;
import com.babylion.team1.domain.feedback.service.FeedbackService;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping("/api/v1/feedbacks/{feedbackId}")
    public ApiResponse<FeedbackDetailResponse> getDetail(
            @PathVariable Long feedbackId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "조회 성공", feedbackService.getDetail(feedbackId, userId));
    }
}
