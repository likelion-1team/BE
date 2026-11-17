package com.babylion.team1.domain.auth.service;

import com.babylion.team1.global.auth.AuthRequestLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter extends AuthRequestLimiter {
	public LoginAttemptLimiter(@Value("${auth.login-attempts-per-minute}") int limit) {
		super(limit);
	}
}
