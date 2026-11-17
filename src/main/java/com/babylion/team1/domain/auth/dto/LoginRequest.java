package com.babylion.team1.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank @Size(max = 50) String companyCode,
		@NotBlank @Size(max = 50) String employeeNumber,
		@NotBlank @Size(max = 72) String password,
		Boolean rememberMe) {
	@Override
	public String toString() {
		return "LoginRequest[redacted]";
	}
}
