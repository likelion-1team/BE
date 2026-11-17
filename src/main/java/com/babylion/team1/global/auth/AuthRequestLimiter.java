package com.babylion.team1.global.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class AuthRequestLimiter {
	private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
			.maximumSize(100_000).expireAfterWrite(Duration.ofMinutes(1)).build();
	private final int limit;

	public AuthRequestLimiter(int limit) {
		if (limit < 1 || limit == Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Invalid request limit");
		}
		this.limit = limit;
	}

	public void check(String key) {
		var counter = attempts.get(key, ignored -> new AtomicInteger());
		if (counter.updateAndGet(count -> count <= limit ? count + 1 : count) > limit) {
			throw AuthException.rateLimited();
		}
	}
}
