package com.babylion.team1.domain.auth.dto.response;

import com.babylion.team1.domain.user.entity.User;

/** 리프레시 토큰은 HttpOnly 쿠키로만 전달하므로 응답 본문에 넣지 않는다. expiresIn은 액세스 토큰의 유효 시간(초). */
public record LoginResponse(
        String accessToken,
        long expiresIn,
        UserInfo user
) {

    public static LoginResponse of(String accessToken, long expiresIn, User user) {
        return new LoginResponse(accessToken, expiresIn, UserInfo.from(user));
    }

    public record UserInfo(
            Long userId,
            String name,
            String email
    ) {
        public static UserInfo from(User user) {
            return new UserInfo(user.getId(), user.getName(), user.getEmail());
        }
    }
}
