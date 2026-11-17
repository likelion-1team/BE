package com.babylion.team1.domain.company.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.company.service.AuthCompaniesService;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthExceptionHandler;
import com.babylion.team1.global.auth.BearerAuthenticationConfig;
import com.babylion.team1.global.auth.JwtTokens;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthCompaniesControllerTest {
	private static final String URL = "/api/v1/auth/companies";
	private static final long USER_ID = 101L;
	private final UserRepository users = mock(UserRepository.class);
	private final JwtTokens tokens = new JwtTokens(testSecret(), 900_000, 1_209_600_000);
	private final MockMvc mvc = MockMvcBuilders
			.standaloneSetup(new AuthCompaniesController(new AuthCompaniesService(users), 1))
			.setCustomArgumentResolvers(new BearerAuthenticationConfig(tokens, users))
			.setControllerAdvice(new AuthCompaniesExceptionHandler(), new AuthExceptionHandler())
			.build();

	@Test
	void returnsOnlyTheAuthenticatedCompanyAndLimitsRepeatedQueries() throws Exception {
		var company = Company.builder().companyCode("DEMO").companyName("데모 고객사").build();
		given(users.existsById(USER_ID)).willReturn(true);
		given(users.findById(USER_ID)).willReturn(Optional.of(User.builder().company(company).build()));
		String authorization = "Bearer " + tokens.issue(USER_ID).accessToken();

		mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{"items":[{"companyCode":"DEMO","companyName":"데모 고객사"}]}
						""", org.springframework.test.json.JsonCompareMode.STRICT));
		mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void requiresAnAccessTokenFromAnExistingUser() throws Exception {
		mvc.perform(get(URL)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(USER_ID).refreshToken()))
				.andExpect(status().isUnauthorized());
		mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(USER_ID).accessToken()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void mapsDatabaseUnavailabilityDuringAuthenticationTo503() throws Exception {
		given(users.existsById(USER_ID)).willThrow(new DataAccessResourceFailureException("database unavailable"));
		mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(USER_ID).accessToken()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	private static String testSecret() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}
}
