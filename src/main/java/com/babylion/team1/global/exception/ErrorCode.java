package com.babylion.team1.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전역 에러 코드.
 * code 값은 enum 이름과 동일하게(UPPER_SNAKE_CASE) 작성한다.
 * 도메인별 코드는 아래에 섹션 주석을 달아 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요."),
    INVALID_JSON(HttpStatus.BAD_REQUEST, "INVALID_JSON", "요청 본문 형식이 올바르지 않습니다."),
    INVALID_TYPE(HttpStatus.BAD_REQUEST, "INVALID_TYPE", "요청 값의 타입이 올바르지 않습니다."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "필수 파라미터가 누락되었습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보가 유효하지 않습니다."),

    // Dashboard
    NO_ACCESSIBLE_PROJECT(HttpStatus.FORBIDDEN, "NO_ACCESSIBLE_PROJECT", "접근 가능한 프로젝트가 없습니다."),
    PROJECT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED", "조회 권한이 없는 프로젝트가 포함되어 있습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "조회 종료일은 시작일보다 이전일 수 없습니다."),

    // Policy
    POLICY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POLICY_ACCESS_DENIED", "정책 조회 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
