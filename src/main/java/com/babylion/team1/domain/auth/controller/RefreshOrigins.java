package com.babylion.team1.domain.auth.controller;

import com.babylion.team1.global.auth.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.CorsFilter;

@Configuration(proxyBeanMethods = false)
public class RefreshOrigins {
	private final List<String> allowed;

	public RefreshOrigins(@Value("${auth.refresh-allowed-origins}") String origins) {
		allowed = Arrays.stream(origins.split(",")).map(String::strip)
				.filter(origin -> !origin.isEmpty() && !origin.equals("*") && !origin.equals("null"))
				.distinct().toList();
	}

	void requireAllowed(HttpServletRequest request) {
		var supplied = Collections.list(request.getHeaders(HttpHeaders.ORIGIN));
		if (supplied.size() != 1 || !allowed.contains(supplied.get(0))) {
			throw AuthException.invalidOrigin();
		}
	}

	@Bean
	FilterRegistrationBean<CorsFilter> refreshCorsFilter() {
		var configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowed);
		configuration.setAllowedMethods(List.of("POST"));
		configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE));
		configuration.setAllowCredentials(true);
		var filter = new CorsFilter(request -> configuration);
		filter.setCorsProcessor(new DefaultCorsProcessor() {
			@Override
			protected void rejectRequest(ServerHttpResponse response) throws IOException {
				response.setStatusCode(HttpStatus.FORBIDDEN);
				response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
				response.getBody().write("""
						{"code":"INVALID_ORIGIN","message":"허용되지 않은 요청 출처입니다."}
						""".getBytes(StandardCharsets.UTF_8));
				response.flush();
			}
		});
		var registration = new FilterRegistrationBean<>(filter);
		registration.addUrlPatterns("/api/v1/auth/refresh", "/api/v1/auth/logout");
		return registration;
	}
}
