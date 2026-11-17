package com.babylion.team1.domain.company.controller;

import com.babylion.team1.domain.company.dto.response.AuthCompaniesResponse;
import com.babylion.team1.domain.company.service.AuthCompaniesService;
import com.babylion.team1.global.auth.AuthRequestLimiter;
import com.babylion.team1.global.auth.AuthenticatedUserId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthCompaniesController {
	private final AuthCompaniesService service;
	private final AuthRequestLimiter limiter;

	public AuthCompaniesController(AuthCompaniesService service,
			@Value("${auth.company-queries-per-minute}") int requestLimit) {
		this.service = service;
		this.limiter = new AuthRequestLimiter(requestLimit);
	}

	@GetMapping("/api/v1/auth/companies")
	public ResponseEntity<AuthCompaniesResponse> getCompanies(@AuthenticatedUserId Long userId) {
		limiter.check(userId.toString());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getCompanies(userId));
	}
}
