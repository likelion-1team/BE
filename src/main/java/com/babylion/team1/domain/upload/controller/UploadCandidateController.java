package com.babylion.team1.domain.upload.controller;

import com.babylion.team1.domain.upload.dto.response.UploadCandidatesResponse;
import com.babylion.team1.domain.upload.service.UploadCandidateQueryService;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UploadCandidateController {
    private final UploadCandidateQueryService queryService;

    @GetMapping("/api/v1/uploads/{uploadId}/conversations")
    public ApiResponse<UploadCandidatesResponse> getCandidates(
            @PathVariable Long uploadId, @AuthenticationPrincipal Long userId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success("S200", "조회 성공", queryService.getCandidates(uploadId, userId, query, page, size));
    }
}
