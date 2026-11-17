package com.babylion.team1.domain.auth.controller;

import com.babylion.team1.domain.auth.dto.LoginRequest;
import com.babylion.team1.domain.auth.dto.LoginResponse;
import com.babylion.team1.domain.auth.service.LoginAttemptLimiter;
import com.babylion.team1.domain.auth.service.LoginService;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.auth.AuthExceptionHandler.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LoginController {
	private final LoginService loginService;
	private final LoginAttemptLimiter limiter;

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		limiter.check(httpRequest.getRemoteAddr());
		var result = loginService.login(request);
		var tokens = result.tokens();
		var cookie = ResponseCookie.from("refreshToken", tokens.refreshToken())
				.httpOnly(true).secure(true).sameSite("Lax").path("/api/v1/auth");
		if (Boolean.TRUE.equals(request.rememberMe())) {
			cookie.maxAge(tokens.refreshLifetime());
		}
		var body = new LoginResponse(tokens.accessToken(), "Bearer", tokens.expiresIn(),
				new LoginResponse.UserInfo(Long.toString(result.userId())));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.header(HttpHeaders.SET_COOKIE, cookie.build().toString()).body(body);
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
	public ResponseEntity<ErrorBody> invalidRequest() {
		var error = AuthException.invalidRequest();
		return ResponseEntity.badRequest().body(new ErrorBody(error.getCode(), error.getMessage()));
	}
}
