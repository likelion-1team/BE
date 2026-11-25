package com.babylion.team1.domain.team.controller;

import com.babylion.team1.domain.team.dto.request.TeamSettingsUpdateRequest;
import com.babylion.team1.domain.team.dto.response.TeamSettingsResponse;
import com.babylion.team1.domain.team.service.TeamSettingsService;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.response.ApiResponse;
import com.babylion.team1.global.response.FieldErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams/{teamId}/settings")
public class TeamSettingsController {

    private final TeamSettingsService teamSettingsService;

    @Operation(summary = "분석 대상·평가 기준 조회")
    @GetMapping
    public ApiResponse<TeamSettingsResponse> getSettings(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "조회 성공", teamSettingsService.getSettings(teamId, userId));
    }

    @Operation(summary = "분석 대상 설정")
    @PutMapping
    public ApiResponse<TeamSettingsResponse> updateSettings(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamSettingsUpdateRequest request) {
        return ApiResponse.success("S200", "설정 저장 성공",
                teamSettingsService.updateSettings(teamId, userId, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSettings(MethodArgumentNotValidException exception) {
        return error(ErrorCode.VALIDATION_FAILED, FieldErrorResponse.listFrom(exception.getBindingResult()));
    }

    /** 허용하지 않은 필드와 잘못된 타입은 422, JSON 문법 오류와 본문 누락은 400으로 응답한다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableSettings(HttpMessageNotReadableException exception) {
        boolean invalidValue = exception.getCause() instanceof DatabindException
                && !(exception.getMostSpecificCause() instanceof StreamReadException);
        return error(invalidValue ? ErrorCode.VALIDATION_FAILED : ErrorCode.INVALID_JSON, null);
    }

    private ResponseEntity<ApiResponse<Void>> error(ErrorCode error, Object errors) {
        return ResponseEntity.status(error.getStatus())
                .body(ApiResponse.error(error.getCode(), error.getMessage(), errors));
    }
}
