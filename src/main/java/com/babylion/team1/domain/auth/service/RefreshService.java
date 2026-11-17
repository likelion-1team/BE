package com.babylion.team1.domain.auth.service;

import com.babylion.team1.domain.auth.entity.RefreshSession;
import com.babylion.team1.domain.auth.repository.RefreshSessionRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.auth.JwtTokens;
import com.babylion.team1.global.auth.JwtTokens.TokenPair;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshService {
	private final RefreshSessionRepository sessions;
	private final JwtTokens tokens;

	public RefreshService(RefreshSessionRepository sessions, @Lazy JwtTokens tokens) {
		this.sessions = sessions;
		this.tokens = tokens;
	}

	@Transactional
	public void register(User user, TokenPair issued, boolean rememberMe) {
		sessions.save(new RefreshSession(user, hash(issued.refreshToken()), issued.refreshExpiresAt(), rememberMe));
	}

	@Transactional
	public RefreshResult refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw AuthException.invalidRefreshToken();
		}
		long userId = tokens.readRefreshUserId(refreshToken);
		String currentHash = hash(refreshToken);
		RefreshSession session = sessions.findByTokenHash(currentHash).orElseThrow(AuthException::invalidRefreshToken);
		if (!currentHash.equals(session.getTokenHash()) || session.getUser().getId() != userId
				|| !session.getExpiresAt().isAfter(Instant.now()) || session.getUser().getCompany() == null) {
			throw AuthException.invalidRefreshToken();
		}

		TokenPair issued = tokens.rotate(userId, session.getExpiresAt());
		session.rotateTo(hash(issued.refreshToken()));
		return new RefreshResult(issued, session.isRememberMe());
	}

	private static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record RefreshResult(TokenPair tokens, boolean rememberMe) {
	}
}
