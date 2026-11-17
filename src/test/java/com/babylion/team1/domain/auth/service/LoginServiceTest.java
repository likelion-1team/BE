package com.babylion.team1.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.babylion.team1.domain.auth.dto.LoginRequest;
import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.company.repository.CompanyRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.auth.JwtTokens;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class LoginServiceTest {
	private static final String PASSWORD = "test-password";
	private final CompanyRepository companies = mock(CompanyRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final JwtTokens jwt = mock(JwtTokens.class);
	private final LoginService service = new LoginService(companies, users, jwt, mock(RefreshService.class));

	@Test
	void sameEmployeeNumberSelectsTheAccountInTheRequestedCompany() {
		var first = account(101L, PASSWORD);
		var second = account(102L, PASSWORD);
		given(companies.findTop2ByCompanyCode("FIRST")).willReturn(List.of(company(1L)));
		given(companies.findTop2ByCompanyCode("SECOND")).willReturn(List.of(company(2L)));
		given(users.findTop2ByCompany_IdAndEmployeeNumber(1L, "EMP-1")).willReturn(List.of(first));
		given(users.findTop2ByCompany_IdAndEmployeeNumber(2L, "EMP-1")).willReturn(List.of(second));
		var tokens = new JwtTokens.TokenPair("access", 900L, "refresh", Instant.EPOCH.plusSeconds(1_209_600));
		given(jwt.issue(101L)).willReturn(tokens);
		given(jwt.issue(102L)).willReturn(tokens);

		assertThat(service.login(new LoginRequest("FIRST", "EMP-1", PASSWORD, false)).userId()).isEqualTo(101L);
		assertThat(service.login(new LoginRequest("SECOND", "EMP-1", PASSWORD, false)).userId()).isEqualTo(102L);
		assertThat(second.getFailedLoginCount()).isZero();
		assertThat(second.getLastLoginAt()).isNotNull();
	}

	@Test
	void invalidPasswordRecordsFailedAttempt() {
		var user = account(101L, PASSWORD);
		given(companies.findTop2ByCompanyCode("FIRST")).willReturn(List.of(company(1L)));
		given(users.findTop2ByCompany_IdAndEmployeeNumber(1L, "EMP-1")).willReturn(List.of(user));

		assertInvalidCredentials(new LoginRequest("FIRST", "EMP-1", "wrong-password", false));
		assertThat(user.getFailedLoginCount()).isEqualTo(3);
		verifyNoInteractions(jwt);
	}

	@Test
	void ambiguousCompanyCodeIsRejectedWithoutSearchingForUsers() {
		given(companies.findTop2ByCompanyCode("FIRST")).willReturn(List.of(company(1L), company(2L)));

		assertInvalidCredentials(new LoginRequest("FIRST", "EMP-1", PASSWORD, false));
		verifyNoInteractions(users, jwt);
	}

	@Test
	void duplicateEmployeeNumberWithinOneCompanyIsRejected() {
		given(companies.findTop2ByCompanyCode("FIRST")).willReturn(List.of(company(1L)));
		given(users.findTop2ByCompany_IdAndEmployeeNumber(1L, "EMP-1"))
				.willReturn(List.of(account(101L, PASSWORD), account(102L, PASSWORD)));

		assertInvalidCredentials(new LoginRequest("FIRST", "EMP-1", PASSWORD, false));
		verifyNoInteractions(jwt);
	}

	private static Company company(long id) {
		var company = Company.builder().build();
		ReflectionTestUtils.setField(company, "id", id);
		return company;
	}

	private static User account(long id, String password) {
		var user = User.builder().passwordHash(new BCryptPasswordEncoder().encode(password)).failedLoginCount(2).build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private void assertInvalidCredentials(LoginRequest request) {
		assertThatThrownBy(() -> service.login(request)).isInstanceOfSatisfying(AuthException.class,
				error -> assertThat(error.getCode()).isEqualTo("INVALID_CREDENTIALS"));
	}
}
