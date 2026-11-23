package com.babylion.team1.domain.auth.dto;

/** 서비스가 컨트롤러에 넘기는 결과. response는 응답 본문으로, refreshToken은 쿠키로 나간다. */
public record AuthResult<T>(
        T response,
        String refreshToken
) {
}
