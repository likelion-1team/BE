package com.babylion.team1.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.babylion.team1.domain.audit.entity.AuditEventType;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.auth.entity.RefreshSession;
import com.babylion.team1.domain.auth.repository.RefreshSessionRepository;
import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.auth.JwtTokens;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshServiceTest {
	private final RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
	private final JwtTokens tokens = new JwtTokens(testSecret(), 900_000, 1_209_600_000);
	private final AuditLogRepository auditLogs = mock(AuditLogRepository.class);
	private final RefreshService service = new RefreshService(sessions, tokens, auditLogs);

	@Test
	void rotatesTheStoredHashAndPreservesSessionExpiryAndRememberMe() throws Exception {
		var initial = tokens.issue(101);
		var session = session(initial.refreshToken(), initial.refreshExpiresAt());
		given(sessions.findByTokenHash(anyString())).willAnswer(invocation ->
				session.getTokenHash().equals(invocation.getArgument(0)) ? Optional.of(session) : Optional.empty());

		var refreshed = service.refresh(initial.refreshToken());
		assertThat(refreshed.rememberMe()).isTrue();
		assertThat(refreshed.tokens().refreshExpiresAt()).isEqualTo(initial.refreshExpiresAt());
		assertThat(refreshed.tokens().refreshToken()).isNotEqualTo(initial.refreshToken());
		assertThat(tokens.readAccessUserId(refreshed.tokens().accessToken())).isEqualTo(101);
		assertInvalid(initial.refreshToken());
		assertThat(service.refresh(refreshed.tokens().refreshToken()).tokens().refreshToken()).isNotBlank();
	}

	@Test
	void rejectsInvalidTokensAndAccessTokensBeforeLookingUpTheirHashes() {
		assertInvalid(null);
		assertInvalid("invalid");
		assertInvalid(tokens.issue(101).accessToken());
		verifyNoInteractions(sessions);
	}

	@Test
	void rejectsExpiredSessionEvenIfThePresentedJwtIsStillValid() throws Exception {
		var issued = tokens.issue(101);
		var session = session(issued.refreshToken(), Instant.EPOCH);
		given(sessions.findByTokenHash(session.getTokenHash())).willReturn(Optional.of(session));
		assertInvalid(issued.refreshToken());
	}

	@Test
	void revokesCurrentSessionAndAuditsOnlyOnceWhenRequestIsRetried() throws Exception {
		var issued = tokens.issue(101);
		var session = session(issued.refreshToken(), issued.refreshExpiresAt());
		given(sessions.findByTokenHash(session.getTokenHash()))
				.willReturn(Optional.of(session)).willReturn(Optional.empty());

		service.revoke(issued.refreshToken());
		service.revoke(issued.refreshToken());

		verify(sessions).delete(session);
		verify(auditLogs).save(argThat(log -> log.getEventType() == AuditEventType.LOGOUT
				&& log.getUser().getId().equals(101L) && log.getDetail() == null));
		assertInvalid(issued.refreshToken());
	}

	@Test
	void expiredSessionDoesNotCreateLogoutAudit() throws Exception {
		var issued = tokens.issue(101);
		var session = session(issued.refreshToken(), Instant.EPOCH);
		given(sessions.findByTokenHash(session.getTokenHash())).willReturn(Optional.of(session));
		service.revoke(issued.refreshToken());
		verifyNoInteractions(auditLogs);
	}

	private void assertInvalid(String token) {
		assertThatThrownBy(() -> service.refresh(token)).isInstanceOfSatisfying(AuthException.class,
				error -> assertThat(error.getCode()).isEqualTo("INVALID_REFRESH_TOKEN"));
	}

	private static RefreshSession session(String token, Instant expiresAt) throws Exception {
		var user = User.builder().company(Company.builder().build()).build();
		ReflectionTestUtils.setField(user, "id", 101L);
		String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8)));
		return new RefreshSession(user, hash, expiresAt, true);
	}

	private static String testSecret() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return Base64.getEncoder().encodeToString(key);
	}
}
