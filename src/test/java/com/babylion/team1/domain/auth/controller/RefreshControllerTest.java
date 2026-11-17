package com.babylion.team1.domain.auth.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.babylion.team1.domain.auth.service.RefreshService;
import com.babylion.team1.domain.auth.service.RefreshService.RefreshResult;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.auth.AuthExceptionHandler;
import com.babylion.team1.global.auth.JwtTokens.TokenPair;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RefreshControllerTest {
	private static final String URL = "/api/v1/auth/refresh";
	private static final String ORIGIN = "https://app.example.test";
	private final RefreshService service = mock(RefreshService.class);
	private final RefreshOrigins origins = new RefreshOrigins(ORIGIN);
	private final MockMvc mvc = MockMvcBuilders
			.standaloneSetup(new RefreshController(service, origins, 1))
			.setControllerAdvice(new AuthExceptionHandler())
			.addFilters(origins.refreshCorsFilter().getFilter()).build();

	@Test
	void returnsAccessTokenAndRotatedCookieThenAppliesRateLimit() throws Exception {
		given(service.refresh("old")).willReturn(new RefreshResult(
				new TokenPair("access", 1800, "rotated", Instant.now().plusSeconds(300)), true));

		mvc.perform(post(URL).header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("refreshToken", "old")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access"))
				.andExpect(jsonPath("$.expiresIn").value(1800))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(cookie().value("refreshToken", "rotated"))
				.andExpect(cookie().httpOnly("refreshToken", true))
				.andExpect(cookie().secure("refreshToken", true));
		mvc.perform(post(URL).header(HttpHeaders.ORIGIN, ORIGIN))
				.andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void rejectsMissingCookieAndUnapprovedOrigin() throws Exception {
		given(service.refresh(null)).willThrow(AuthException.invalidRefreshToken());
		mvc.perform(post(URL).header(HttpHeaders.ORIGIN, ORIGIN))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
		mvc.perform(post(URL).header(HttpHeaders.ORIGIN, "https://other.example.test"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_ORIGIN"));
		mvc.perform(post(URL))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_ORIGIN"));
	}

	@Test
	void acceptsCredentialedPreflightFromTheAllowedOrigin() throws Exception {
		mvc.perform(options(URL).header(HttpHeaders.ORIGIN, ORIGIN)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}
}
