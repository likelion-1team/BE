package com.babylion.team1.domain.feedback.controller;

import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse;
import com.babylion.team1.domain.feedback.service.SharePreviewService;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SharePreviewController {
    private final SharePreviewService previews;

    @PostMapping("/api/v1/feedbacks/{feedbackId}/share-preview")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SharePreviewResponse> create(@PathVariable Long feedbackId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S201", "미리보기 생성 성공", previews.create(feedbackId, userId));
    }
}
