package com.babylion.team1.domain.auth.service;

import com.babylion.team1.domain.auth.dto.LoginRequest;
import com.babylion.team1.domain.company.repository.CompanyRepository;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.auth.JwtTokens;
import com.babylion.team1.global.auth.JwtTokens.TokenPair;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {
	private final CompanyRepository companies;
	private final UserRepository users;
	private final JwtTokens tokens;
	private final RefreshService refreshService;
	private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
	private final String dummyHash = passwords.encode(UUID.randomUUID().toString());

	public LoginService(CompanyRepository companies, UserRepository users, @Lazy JwtTokens tokens,
			RefreshService refreshService) {
		this.companies = companies;
		this.users = users;
		this.tokens = tokens;
		this.refreshService = refreshService;
	}

	@Transactional(noRollbackFor = AuthException.class)
	public LoginResult login(LoginRequest request) {
		if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw AuthException.invalidRequest();
		}
		var customers = companies.findTop2ByCompanyCode(request.companyCode());
		if (customers.size() != 1) {
			throw unknownAccount(request.password());
		}
		var candidates = users.findTop2ByCompany_IdAndEmployeeNumber(customers.get(0).getId(),
				request.employeeNumber());
		if (candidates.size() != 1) {
			throw unknownAccount(request.password());
		}
		var user = candidates.get(0);
		if (!passwords.matches(request.password(), user.getPasswordHash())) {
			user.recordFailedLogin();
			throw AuthException.invalidCredentials();
		}

		TokenPair issued = tokens.issue(user.getId());
		refreshService.register(user, issued, Boolean.TRUE.equals(request.rememberMe()));
		user.recordSuccessfulLogin(LocalDateTime.now(ZoneOffset.UTC));
		return new LoginResult(user.getId(), issued);
	}

	private AuthException unknownAccount(String password) {
		passwords.matches(password, dummyHash);
		return AuthException.invalidCredentials();
	}

	public record LoginResult(long userId, TokenPair tokens) {
	}
}
