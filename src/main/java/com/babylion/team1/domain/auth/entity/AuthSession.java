package com.babylion.team1.domain.auth.entity;

import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 로그인 세션. 로그인할 때마다 하나씩 생기고 재발급·로그아웃은 이 단위로 처리한다.
 * 리프레시 토큰은 현재 유효한 것 하나의 해시만 저장한다.
 * 서명은 맞지만 해시가 다른 토큰은 이미 교체된(재사용된) 토큰이다.
 */
@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

	/** 토큰의 sid 클레임에 들어가는 UUID */
	@Id
	@Column(name = "id", length = 36)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** 현재 리프레시 토큰의 SHA-256 해시(hex) */
	@Column(name = "refresh_token_hash", nullable = false, length = 64)
	private String refreshTokenHash;

	@Column(name = "revoked", nullable = false)
	private boolean revoked;

	/** 현재 리프레시 토큰의 만료 시각. 재발급할 때마다 늘어난다. */
	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	private AuthSession(String id, User user, String refreshTokenHash, LocalDateTime expiresAt) {
		this.id = id;
		this.user = user;
		this.refreshTokenHash = refreshTokenHash;
		this.expiresAt = expiresAt;
	}

	public boolean isActive(LocalDateTime now) {
		return !revoked && expiresAt.isAfter(now);
	}

	public boolean hasRefreshTokenHash(String hash) {
		return refreshTokenHash.equals(hash);
	}

	/** 이전 리프레시 토큰은 이 시점부터 재사용으로 취급된다. */
	public void rotate(String newRefreshTokenHash, LocalDateTime newExpiresAt) {
		this.refreshTokenHash = newRefreshTokenHash;
		this.expiresAt = newExpiresAt;
	}

	public void revoke() {
		this.revoked = true;
	}
}
