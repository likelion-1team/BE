package com.babylion.team1.global.exception;

import com.babylion.team1.global.response.ApiResponse;
import com.babylion.team1.global.response.FieldErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리기.
 * 모든 에러 응답은 공통 응답 포맷(ApiResponse.error)으로 내려간다.
 * 로그 기준: 4xx는 warn(메시지만), 5xx는 error(스택트레이스 포함).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 비즈니스 예외 */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("[CustomException] {} - {}", errorCode.getCode(), e.getMessage(), e);
        } else {
            log.warn("[CustomException] {} - {}", errorCode.getCode(), e.getMessage());
        }
        return toResponse(errorCode, e.getMessage(), null);
    }

    /** @Valid @RequestBody 검증 실패 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<FieldErrorResponse> errors = toFieldErrors(e.getBindingResult());
        log.warn("[Validation] {}", errors);
        return toResponse(ErrorCode.INVALID_REQUEST, errors);
    }

    /** @ModelAttribute 바인딩·검증 실패 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
        List<FieldErrorResponse> errors = toFieldErrors(e.getBindingResult());
        log.warn("[Validation] {}", errors);
        return toResponse(ErrorCode.INVALID_REQUEST, errors);
    }

    /** @RequestParam, @PathVariable 등에 붙인 제약 조건(@Min 등) 검증 실패 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        List<FieldErrorResponse> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorResponse(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        log.warn("[Validation] {}", errors);
        return toResponse(ErrorCode.INVALID_REQUEST, errors);
    }

    /** 클래스에 @Validated를 붙인 경우의 메서드 파라미터 검증 실패 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldErrorResponse> errors = e.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(lastPathNode(violation), violation.getMessage()))
                .toList();
        log.warn("[Validation] {}", errors);
        return toResponse(ErrorCode.INVALID_REQUEST, errors);
    }

    /** JSON 문법 오류, 타입 변환 불가, body 누락 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("[InvalidJson] {}", e.getMessage());
        return toResponse(ErrorCode.INVALID_JSON, null);
    }

    /** @PathVariable, @RequestParam 타입 불일치 (예: Long 자리에 "abc") */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        String requiredType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "올바른";
        List<FieldErrorResponse> errors = List.of(
                new FieldErrorResponse(e.getName(), requiredType + " 타입이어야 합니다."));
        log.warn("[InvalidType] {} - {}", e.getName(), e.getValue());
        return toResponse(ErrorCode.INVALID_TYPE, errors);
    }

    /** 필수 @RequestParam 누락 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        List<FieldErrorResponse> errors = List.of(
                new FieldErrorResponse(e.getParameterName(), "필수 파라미터입니다."));
        log.warn("[MissingParameter] {}", e.getParameterName());
        return toResponse(ErrorCode.MISSING_PARAMETER, errors);
    }

    /** 지원하지 않는 HTTP 메서드 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[MethodNotAllowed] {}", e.getMethod());
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED, null);
    }

    /** 지원하지 않는 Content-Type */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("[UnsupportedMediaType] {}", e.getContentType());
        return toResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
    }

    /** 존재하지 않는 경로 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("[NotFound] {}", e.getResourcePath());
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND, null);
    }

    /** 처리되지 않은 모든 예외. 내부 메시지는 클라이언트에 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[UnhandledException] {}", e.getMessage(), e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, Object errors) {
        return toResponse(errorCode, errorCode.getMessage(), errors);
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message, Object errors) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), message, errors));
    }

    private List<FieldErrorResponse> toFieldErrors(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fieldError -> new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
    }

    private String lastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
