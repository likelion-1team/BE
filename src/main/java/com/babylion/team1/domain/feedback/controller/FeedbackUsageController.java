package com.babylion.team1.domain.feedback.controller;

import com.babylion.team1.domain.feedback.dto.request.FeedbackUsageRequest;
import com.babylion.team1.domain.feedback.dto.response.FeedbackUsageResponse;
import com.babylion.team1.domain.feedback.service.FeedbackUsageService;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FeedbackUsageController {
    private final FeedbackUsageService usage;

    @PostMapping("/api/v1/feedbacks/{feedbackId}/usage-events")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FeedbackUsageResponse> record(@PathVariable Long feedbackId,
            @AuthenticationPrincipal Long userId, @RequestBody FeedbackUsageRequest request) {
        var data = usage.record(feedbackId, userId, request);
        return ApiResponse.success("S201", "활용 기록 성공", data, data.recordedAt());
    }
}
