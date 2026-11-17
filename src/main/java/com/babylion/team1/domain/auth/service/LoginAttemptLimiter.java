package com.babylion.team1.domain.auth.service;

import com.babylion.team1.global.auth.AuthException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {
	private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
			.maximumSize(100_000).expireAfterWrite(Duration.ofMinutes(1)).build();
	private final int limit;

	public LoginAttemptLimiter(@Value("${auth.login-attempts-per-minute}") int limit) {
		if (limit < 1 || limit == Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Invalid login attempt limit");
		}
		this.limit = limit;
	}

	public void check(String clientAddress) {
		var counter = attempts.get(clientAddress, ignored -> new AtomicInteger());
		if (counter.updateAndGet(count -> count <= limit ? count + 1 : count) > limit) {
			throw AuthException.rateLimited();
		}
	}
}
