package com.babylion.team1.domain.auth.dto.response;

import com.babylion.team1.domain.user.entity.User;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        UserInfo user
) {

    public static LoginResponse of(String accessToken, String refreshToken, User user) {
        return new LoginResponse(accessToken, refreshToken, UserInfo.from(user));
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
