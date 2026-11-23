package com.babylion.team1.domain.auth.dto.response;

/** 새 리프레시 토큰은 HttpOnly 쿠키로만 전달한다. expiresIn은 액세스 토큰의 유효 시간(초). */
public record TokenRefreshResponse(
        String accessToken,
        long expiresIn
) {
}
