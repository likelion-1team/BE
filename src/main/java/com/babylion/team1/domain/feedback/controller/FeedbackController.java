package com.babylion.team1.domain.feedback.controller;

import com.babylion.team1.domain.feedback.dto.request.FeedbackReactionRequest;
import com.babylion.team1.domain.feedback.dto.request.FeedbackShareRequest;
import com.babylion.team1.domain.feedback.dto.response.FeedbackDetailResponse;
import com.babylion.team1.domain.feedback.dto.response.FeedbackReactionResponse;
import com.babylion.team1.domain.feedback.dto.response.FeedbackShareResponse;
import com.babylion.team1.domain.feedback.service.FeedbackService;
import com.babylion.team1.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PutMapping("/api/v1/feedbacks/{feedbackId}/reaction")
    public ApiResponse<FeedbackReactionResponse> recordReaction(
            @PathVariable Long feedbackId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FeedbackReactionRequest request) {
        return ApiResponse.success("S200", "반응 기록 성공",
                feedbackService.recordReaction(feedbackId, userId, request.type(), request.reason()));
    }

    @DeleteMapping("/api/v1/feedbacks/{feedbackId}/reaction")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelReaction(@PathVariable Long feedbackId, @AuthenticationPrincipal Long userId) {
        feedbackService.cancelReaction(feedbackId, userId);
    }
}
