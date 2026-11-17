package com.babylion.team1.domain.auth.controller;

import com.babylion.team1.global.auth.JwtTokens.TokenPair;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.ResponseCookie;

final class RefreshCookie {
	static final String NAME = "refreshToken";

	private RefreshCookie() {
	}

	static ResponseCookie create(TokenPair tokens, boolean rememberMe) {
		var cookie = builder(tokens.refreshToken());
		if (rememberMe) {
			long remaining = Duration.between(Instant.now().truncatedTo(ChronoUnit.SECONDS), tokens.refreshExpiresAt()).toSeconds();
			cookie.maxAge(Math.max(0, remaining));
		}
		return cookie.build();
	}

	static ResponseCookie expired() {
		return builder("").maxAge(0).build();
	}

	private static ResponseCookie.ResponseCookieBuilder builder(String value) {
		return ResponseCookie.from(NAME, value).httpOnly(true).secure(true).sameSite("Lax").path("/api/v1/auth");
	}
}
