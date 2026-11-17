package com.babylion.team1.domain.auth.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.babylion.team1.domain.auth.service.RefreshService;
import com.babylion.team1.global.auth.AuthExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LogoutControllerTest {
	private static final String URL = "/api/v1/auth/logout";
	private static final String ORIGIN = "https://app.example.test";
	private final RefreshService sessions = mock(RefreshService.class);
	private final RefreshOrigins origins = new RefreshOrigins(ORIGIN);
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new LogoutController(sessions, origins))
			.setControllerAdvice(new AuthExceptionHandler())
			.addFilters(origins.refreshCorsFilter().getFilter()).build();

	@Test
	void expiresCookieAndRevokesSession() throws Exception {
		mvc.perform(post(URL).header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("refreshToken", "current")))
				.andExpect(status().isNoContent()).andExpect(content().string(""))
				.andExpect(cookie().maxAge("refreshToken", 0))
				.andExpect(cookie().path("refreshToken", "/api/v1/auth"))
				.andExpect(cookie().httpOnly("refreshToken", true))
				.andExpect(cookie().secure("refreshToken", true));
		verify(sessions).revoke("current");
	}

	@Test
	void expiresCookieEvenWhenNoCookieWasSent() throws Exception {
		mvc.perform(post(URL).header(HttpHeaders.ORIGIN, ORIGIN))
				.andExpect(status().isNoContent()).andExpect(cookie().maxAge("refreshToken", 0));
		verifyNoInteractions(sessions);
	}

	@Test
	void rejectsUnapprovedOrMissingOrigin() throws Exception {
		mvc.perform(post(URL)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INVALID_ORIGIN"));
		mvc.perform(post(URL).header(HttpHeaders.ORIGIN, "https://other.example.test"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_ORIGIN"));
		verifyNoInteractions(sessions);
	}

	@Test
	void reportsDbUnavailableAndPreservesCookieForRetry() throws Exception {
		doThrow(new DataAccessResourceFailureException("unavailable")).when(sessions).revoke("current");
		mvc.perform(post(URL).header(HttpHeaders.ORIGIN, ORIGIN).cookie(new Cookie("refreshToken", "current")))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
}
