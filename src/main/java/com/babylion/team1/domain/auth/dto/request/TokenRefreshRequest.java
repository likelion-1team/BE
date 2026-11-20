package com.babylion.team1.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 토큰 재발급 요청 */
public record TokenRefreshRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        String refreshToken
) {
}
