package com.babylion.team1.domain.deletion.controller;

import com.babylion.team1.domain.deletion.dto.response.DeletionJobResponse;
import com.babylion.team1.domain.deletion.dto.response.DeletionRequestResponse;
import com.babylion.team1.domain.deletion.service.DeletionService;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DeletionController {

    private final DeletionService deletionService;

    @DeleteMapping("/api/v1/conversation-versions/{conversationVersionId}")
    public ResponseEntity<ApiResponse<DeletionRequestResponse>> requestDeletion(
            @Parameter(description = "입력 버전 ID. 다른 응답의 conversationVersionId 값을 보낸다")
            @PathVariable Long conversationVersionId,
            @AuthenticationPrincipal Long userId) {
        var response = deletionService.requestDeletion(conversationVersionId, userId);
        return ResponseEntity.accepted().location(URI.create(response.statusUrl()))
                .body(ApiResponse.success("S202", "삭제 요청 수락", response));
    }

    @GetMapping("/api/v1/deletion-jobs/{deletionJobId}")
    public ApiResponse<DeletionJobResponse> getJob(
            @PathVariable Long deletionJobId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "조회 성공", deletionService.getJob(deletionJobId, userId));
    }
}
