package com.babylion.team1.domain.team.controller;

import com.babylion.team1.domain.team.dto.request.TeamGoalUpdateRequest;
import com.babylion.team1.domain.team.dto.response.TeamGoalResponse;
import com.babylion.team1.domain.team.service.TeamGoalService;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.response.ApiResponse;
import com.babylion.team1.global.response.FieldErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/goals")
public class TeamGoalController {

    private final TeamGoalService teamGoalService;

    @Operation(summary = "팀 목표 조회")
    @GetMapping
    public ApiResponse<TeamGoalResponse> getGoals(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "조회 성공", teamGoalService.getGoals(teamId, userId));
    }

    @Operation(summary = "팀 목표 수정")
    @PutMapping
    public ApiResponse<TeamGoalResponse> updateGoals(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamGoalUpdateRequest request) {
        return ApiResponse.success("S200", "목표 저장 성공", teamGoalService.updateGoals(teamId, userId, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidGoals(MethodArgumentNotValidException exception) {
        ErrorCode error = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(error.getStatus())
                .body(ApiResponse.error(error.getCode(), error.getMessage(),
                        FieldErrorResponse.listFrom(exception.getBindingResult())));
    }
}
