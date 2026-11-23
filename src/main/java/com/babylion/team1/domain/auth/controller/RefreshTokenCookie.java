package com.babylion.team1.domain.auth.controller;

import com.babylion.team1.global.jwt.JwtProvider;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 쿠키. 자바스크립트로 읽을 수 없고(HttpOnly) 인증 API 경로로만 전송된다.
 * 삭제할 때도 속성이 같아야 브라우저가 같은 쿠키로 인식한다.
 */
@Component
@RequiredArgsConstructor
class RefreshTokenCookie {

    static final String NAME = "refreshToken";
    private static final String PATH = "/api/v1/auth";

    private final JwtProvider jwtProvider;

    String issue(String refreshToken) {
        return build(refreshToken, Duration.ofSeconds(jwtProvider.getRefreshTokenExpirationSeconds()));
    }

    String expire() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
