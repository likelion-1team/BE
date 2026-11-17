package com.babylion.team1.global.auth;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-1)
@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler(AuthException.class)
	public ResponseEntity<ErrorBody> handle(AuthException exception) {
		var response = ResponseEntity.status(exception.getStatus());
		if ("UNAUTHORIZED".equals(exception.getCode())) {
			response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		}
		return response.body(new ErrorBody(exception.getCode(), exception.getMessage()));
	}

	public record ErrorBody(String code, String message) {
	}
}
