package com.babylion.team1.domain.upload.controller;

import com.babylion.team1.domain.upload.dto.request.AnalysisStartRequest;
import com.babylion.team1.domain.upload.dto.response.AnalysisStartResponse;
import com.babylion.team1.domain.upload.service.AnalysisStartService;
import com.babylion.team1.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisStartService analysisStartService;

    @PostMapping(path = "/api/v1/uploads/{uploadId}/analyses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AnalysisStartResponse>> start(
            @PathVariable Long uploadId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AnalysisStartRequest request) {
        var response = analysisStartService.start(uploadId, userId, request.selections());
        return ResponseEntity.accepted().body(ApiResponse.success("S202", "분석 시작", response));
    }
}
