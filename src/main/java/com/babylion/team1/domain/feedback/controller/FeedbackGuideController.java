package com.babylion.team1.domain.feedback.controller;

import com.babylion.team1.domain.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/feedbacks")
public class FeedbackGuideController {

    private final TeamService teamService;

    @Operation(summary = "개선 가이드 해제")
    @DeleteMapping("/{feedbackId}/guide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeGuide(
            @PathVariable Long feedbackId,
            @AuthenticationPrincipal Long userId) {
        teamService.removeGuide(feedbackId, userId);
    }
}
