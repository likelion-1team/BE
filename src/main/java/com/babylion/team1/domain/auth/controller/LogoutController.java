package com.babylion.team1.domain.auth.controller;

import com.babylion.team1.domain.auth.service.RefreshService;
import com.babylion.team1.global.auth.AuthExceptionHandler.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LogoutController {
	private final RefreshService sessions;
	private final RefreshOrigins origins;

	@PostMapping("/api/v1/auth/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request,
			@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
		origins.requireAllowed(request);
		if (refreshToken != null && !refreshToken.isBlank()) {
			sessions.revoke(refreshToken);
		}
		return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
				.header(HttpHeaders.SET_COOKIE, RefreshCookie.expired().toString()).build();
	}

	@ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessResourceException.class,
			CannotCreateTransactionException.class})
	public ResponseEntity<ErrorBody> unavailable() {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorBody("SERVICE_UNAVAILABLE", "잠시 후 다시 시도해 주세요."));
	}
}
