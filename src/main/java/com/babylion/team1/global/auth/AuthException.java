package com.babylion.team1.global.auth;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {
	private final HttpStatus status;
	private final String code;

	private AuthException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public static AuthException unauthorized() {
		return new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증 정보가 유효하지 않습니다.");
	}

	public static AuthException invalidCredentials() {
		return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "로그인 정보가 유효하지 않습니다.");
	}

	public static AuthException invalidRefreshToken() {
		return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "갱신 토큰이 유효하지 않습니다.");
	}

	public static AuthException invalidOrigin() {
		return new AuthException(HttpStatus.FORBIDDEN, "INVALID_ORIGIN", "허용되지 않은 요청 출처입니다.");
	}

	public static AuthException invalidRequest() {
		return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보를 확인해 주세요.");
	}

	public static AuthException rateLimited() {
		return new AuthException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "잠시 후 다시 시도해 주세요.");
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
