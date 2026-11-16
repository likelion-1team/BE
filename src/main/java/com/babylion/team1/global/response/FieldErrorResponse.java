package com.babylion.team1.global.response;

/**
 * 에러 응답(ApiResponse.errors)에 담기는 필드별 오류 정보.
 * 비밀번호 같은 민감한 값이 응답에 노출되지 않도록 입력값(rejected value)은 담지 않는다.
 */
public record FieldErrorResponse(
        String field,
        String reason
) {
}
