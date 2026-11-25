package com.babylion.team1.domain.conversation.controller;

import com.babylion.team1.domain.conversation.dto.response.ConversationDetailResponse;
import com.babylion.team1.domain.conversation.service.ConversationService;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping("/api/v1/conversation-versions/{conversationVersionId}")
    public ApiResponse<ConversationDetailResponse> getDetail(
            @PathVariable Long conversationVersionId,
            @RequestParam(required = false) Long highlightFeedbackId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "조회 성공",
                conversationService.getDetail(conversationVersionId, highlightFeedbackId, userId));
    }
}
