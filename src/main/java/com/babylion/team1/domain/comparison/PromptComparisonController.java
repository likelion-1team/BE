package com.babylion.team1.domain.comparison;

import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/prompt-comparison")
public class PromptComparisonController {

    private final PromptComparisonService service;

    @Operation(summary = "OpenAI 프롬프트 비교 설정 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<PromptComparisonResult.Configuration>> configuration(
            @PathVariable Long teamId, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("S200", "조회 성공", service.configuration(teamId, userId)));
    }

    @Operation(summary = "같은 업무의 기존·개선 프롬프트 실행 및 실제 토큰 비교")
    @PostMapping
    public ResponseEntity<ApiResponse<PromptComparisonResult>> compare(
            @PathVariable Long teamId, @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PromptComparisonRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("S200", "비교 실행 완료", service.compare(teamId, userId, request)));
    }
}
