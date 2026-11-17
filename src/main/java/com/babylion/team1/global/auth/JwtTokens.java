package com.babylion.team1.global.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Lazy
@Component
public class JwtTokens {
	private final NimbusJwtEncoder encoder;
	private final NimbusJwtDecoder decoder;
	private final Duration accessLifetime;
	private final Duration refreshLifetime;

	public JwtTokens(@Value("${jwt.secret}") String base64Secret,
			@Value("${jwt.access-token-expiration}") long accessMillis,
			@Value("${jwt.refresh-token-expiration}") long refreshMillis) {
		byte[] secret = Base64.getDecoder().decode(base64Secret);
		if (secret.length < 32 || accessMillis < 1000 || refreshMillis < 1000) {
			throw new IllegalArgumentException("JWT configuration requires a Base64 key of at least 32 bytes and valid lifetimes");
		}
		var key = new SecretKeySpec(secret, "HmacSHA256");
		encoder = NimbusJwtEncoder.withSecretKey(key).build();
		decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
		accessLifetime = Duration.ofSeconds(accessMillis / 1000);
		refreshLifetime = Duration.ofSeconds(refreshMillis / 1000);
	}

	public TokenPair issue(long userId) {
		Instant now = Instant.now();
		return new TokenPair(encode(userId, now, accessLifetime, "access"), accessLifetime.toSeconds(),
				encode(userId, now, refreshLifetime, "refresh"), refreshLifetime);
	}

	public long readAccessUserId(String token) {
		try {
			var jwt = decoder.decode(token);
			if (!"access".equals(jwt.getClaimAsString("token_use")) || jwt.getExpiresAt() == null
					|| !jwt.getExpiresAt().isAfter(Instant.now())) {
				throw AuthException.unauthorized();
			}
			long userId = Long.parseLong(jwt.getSubject());
			if (userId < 1) {
				throw AuthException.unauthorized();
			}
			return userId;
		} catch (JwtException | IllegalArgumentException exception) {
			throw AuthException.unauthorized();
		}
	}

	private String encode(long userId, Instant now, Duration lifetime, String use) {
		var claims = JwtClaimsSet.builder().subject(Long.toString(userId)).issuedAt(now)
				.expiresAt(now.plus(lifetime)).id(UUID.randomUUID().toString()).claim("token_use", use).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	public record TokenPair(String accessToken, long expiresIn, String refreshToken, Duration refreshLifetime) {
		@Override
		public String toString() {
			return "TokenPair[expiresIn=" + expiresIn + ", refreshLifetime=" + refreshLifetime + "]";
		}
	}
}
