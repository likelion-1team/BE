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
    ANALYSIS_ALREADY_COMPLETED(HttpStatus.CONFLICT, "ANALYSIS_ALREADY_COMPLETED", "이미 분석이 완료되었습니다."),
    DUPLICATE_CONTENT(HttpStatus.CONFLICT, "DUPLICATE_CONTENT", "이미 등록된 대화 내용입니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "같은 키로 다른 요청을 보낼 수 없습니다."),
    UPLOAD_CONSUMED(HttpStatus.CONFLICT, "UPLOAD_CONSUMED", "이미 분석이 확정된 업로드입니다."),
    UPLOAD_EXPIRED(HttpStatus.GONE, "UPLOAD_EXPIRED", "분석할 원본 파일의 보관 기간이 만료되었습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),

    // Auth
    SHARING_REDACTION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "SHARING_REDACTION_FAILED", "공유 내용의 비식별화를 확인할 수 없습니다."),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "입력값을 확인해 주세요."),
    EMAIL_IN_USE(HttpStatus.CONFLICT, "EMAIL_IN_USE", "이미 가입된 이메일입니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "로그인이 필요합니다."),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "세션이 만료되었습니다. 다시 로그인해 주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),

    // Team
    TARGET_NOT_SET(HttpStatus.BAD_REQUEST, "TARGET_NOT_SET", "팀장이 분석 대상 업무와 기간을 먼저 설정해야 합니다."),
    NOT_A_MEMBER(HttpStatus.FORBIDDEN, "NOT_A_MEMBER", "팀 멤버만 접근할 수 있습니다."),
    LEADER_ONLY(HttpStatus.FORBIDDEN, "LEADER_ONLY", "팀장만 사용할 수 있는 기능입니다."),
    ROLE_FORBIDDEN(HttpStatus.FORBIDDEN, "ROLE_FORBIDDEN", "이 기능을 사용할 권한이 없습니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "INVALID_STATE", "현재 상태에서는 처리할 수 없습니다."),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "VERSION_CONFLICT", "저장된 버전이 변경되었습니다. 최신 값을 확인해 주세요."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", "팀을 찾을 수 없습니다."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "ALREADY_MEMBER", "이미 팀 멤버입니다."),

    // Upload
    INVALID_FILE_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_FILE_FORMAT", "ChatGPT 내보내기 파일 형식을 확인해 주세요."),
    UPLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "UPLOAD_TOO_LARGE", "업로드 파일이 허용 용량을 초과했습니다."),
    EXPANDED_SIZE_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "EXPANDED_SIZE_EXCEEDED", "압축 파일이 허용 한도를 초과했습니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "지원하지 않는 업로드 파일 형식입니다."),
    UNSUPPORTED_STRUCTURE(HttpStatus.UNPROCESSABLE_CONTENT, "UNSUPPORTED_STRUCTURE", "ChatGPT 내보내기 파일 구조를 확인해 주세요."),
    UNSAFE_ARCHIVE(HttpStatus.UNPROCESSABLE_CONTENT, "UNSAFE_ARCHIVE", "안전하지 않은 압축 파일입니다."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "파일이 허용 용량을 초과했습니다."),

    // Analysis
    NO_CONVERSATION_SELECTED(HttpStatus.BAD_REQUEST, "NO_CONVERSATION_SELECTED", "분석할 대화를 선택해 주세요."),
    TASK_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "TASK_TYPE_NOT_ALLOWED", "팀에서 허용하지 않는 업무 유형입니다."),
    NOT_UPLOADER(HttpStatus.FORBIDDEN, "NOT_UPLOADER", "파일을 올린 본인만 분석할 수 있습니다."),
    UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND", "업로드를 찾을 수 없습니다."),
    ANALYSIS_IN_PROGRESS(HttpStatus.CONFLICT, "ANALYSIS_IN_PROGRESS", "이미 분석 중입니다."),

    // Feedback
    NOT_CONVERSATION_OWNER(HttpStatus.FORBIDDEN, "NOT_CONVERSATION_OWNER", "본인 대화만 조회할 수 있습니다."),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "대화를 찾을 수 없습니다."),
    NOT_FEEDBACK_OWNER(HttpStatus.FORBIDDEN, "NOT_FEEDBACK_OWNER", "피드백 대상자만 조회할 수 있습니다."),
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "FEEDBACK_NOT_FOUND", "피드백을 찾을 수 없습니다."),
    FEEDBACK_NOT_SHARED(HttpStatus.BAD_REQUEST, "FEEDBACK_NOT_SHARED", "공유가 허용된 사례만 가이드로 지정할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
