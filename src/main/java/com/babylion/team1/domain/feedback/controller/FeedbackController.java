package com.babylion.team1.domain.feedback.controller;

import com.babylion.team1.domain.feedback.dto.request.FeedbackShareRequest;
import com.babylion.team1.domain.feedback.dto.response.FeedbackDetailResponse;
import com.babylion.team1.domain.feedback.dto.response.FeedbackShareResponse;
import com.babylion.team1.domain.feedback.service.FeedbackService;
import com.babylion.team1.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PatchMapping("/api/v1/feedbacks/{feedbackId}/share")
    public ApiResponse<FeedbackShareResponse> updateShare(
            @PathVariable Long feedbackId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FeedbackShareRequest request) {
        return ApiResponse.success("S200", "공유 설정 변경 성공",
                feedbackService.updateShare(feedbackId, userId, request.shared()));
    }
}
