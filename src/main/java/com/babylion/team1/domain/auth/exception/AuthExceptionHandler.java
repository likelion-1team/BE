package com.babylion.team1.domain.auth.exception;

import com.babylion.team1.domain.auth.controller.AuthController;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.response.ApiResponse;
import com.babylion.team1.global.response.FieldErrorResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 인증 API의 입력값 검증 실패는 v4 명세에 따라 422 VALIDATION_FAILED로 응답한다.
 * 다른 API는 기존처럼 GlobalExceptionHandler가 400 INVALID_REQUEST로 응답한다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    /** @RequestBody 검증 실패(MethodArgumentNotValidException)도 BindException의 하위 타입이라 함께 처리된다. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(BindException e) {
        List<FieldErrorResponse> errors = FieldErrorResponse.listFrom(e.getBindingResult());
        log.warn("[Validation] {}", errors);
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), errors));
    }
}
