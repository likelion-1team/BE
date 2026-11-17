package com.babylion.team1.domain.auth.controller;

import com.babylion.team1.domain.auth.service.RefreshService;
import com.babylion.team1.global.auth.AuthRequestLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/refresh")
public class RefreshController {
	private final RefreshService service;
	private final RefreshOrigins origins;
	private final AuthRequestLimiter limiter;

	public RefreshController(RefreshService service, RefreshOrigins origins,
			@Value("${auth.refresh-attempts-per-minute}") int attemptLimit) {
		this.service = service;
		this.origins = origins;
		this.limiter = new AuthRequestLimiter(attemptLimit);
	}

	@PostMapping
	public ResponseEntity<RefreshResponse> refresh(HttpServletRequest request,
			@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
		origins.requireAllowed(request);
		limiter.check(request.getRemoteAddr());

		var result = service.refresh(refreshToken);
		var tokens = result.tokens();
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.header(HttpHeaders.SET_COOKIE, RefreshCookie.create(tokens, result.rememberMe()).toString())
				.body(new RefreshResponse(tokens.accessToken(), "Bearer", tokens.expiresIn()));
	}

	public record RefreshResponse(String accessToken, String tokenType, long expiresIn) {
		@Override
		public String toString() {
			return "RefreshResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
		}
	}
}
