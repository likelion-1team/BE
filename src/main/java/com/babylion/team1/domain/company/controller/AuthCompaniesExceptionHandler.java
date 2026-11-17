package com.babylion.team1.domain.company.controller;

import com.babylion.team1.global.auth.AuthExceptionHandler.ErrorBody;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-2)
@RestControllerAdvice(assignableTypes = AuthCompaniesController.class)
public class AuthCompaniesExceptionHandler {
	@ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessResourceException.class,
			CannotCreateTransactionException.class})
	public ResponseEntity<ErrorBody> unavailable() {
		return ResponseEntity.status(503)
				.body(new ErrorBody("SERVICE_UNAVAILABLE", "잠시 후 다시 시도해 주세요."));
	}
}
