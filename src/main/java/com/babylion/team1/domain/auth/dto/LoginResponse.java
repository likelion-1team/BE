package com.babylion.team1.domain.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserInfo user) {
	public record UserInfo(String userId) {
	}

	@Override
	public String toString() {
		return "LoginResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", user=" + user + "]";
	}
}
