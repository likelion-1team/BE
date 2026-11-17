package com.babylion.team1.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtTokensTest {
	private final JwtTokens tokens = new JwtTokens(secret((byte) 1), 900_000, 1_209_600_000);

	@Test
	void acceptsAnAccessTokenIssuedForTheUser() {
		var result = tokens.issue(101L);
		assertThat(result.expiresIn()).isEqualTo(900);
		assertThat(tokens.readAccessUserId(result.accessToken())).isEqualTo(101L);
	}

	@Test
	void rejectsRefreshTokensAndTokensSignedWithAnotherKey() {
		var otherIssuer = new JwtTokens(secret((byte) 2), 900_000, 1_209_600_000);
		assertUnauthorized(tokens.issue(101L).refreshToken());
		assertUnauthorized(otherIssuer.issue(101L).accessToken());
		assertUnauthorized("invalid");
	}

	private void assertUnauthorized(String token) {
		assertThatThrownBy(() -> tokens.readAccessUserId(token)).isInstanceOfSatisfying(AuthException.class,
				error -> assertThat(error.getCode()).isEqualTo("UNAUTHORIZED"));
	}

	private static String secret(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return Base64.getEncoder().encodeToString(key);
	}
}
