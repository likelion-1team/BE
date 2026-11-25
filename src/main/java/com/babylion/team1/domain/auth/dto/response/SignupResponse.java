package com.babylion.team1.domain.auth.dto.response;

import com.babylion.team1.domain.user.entity.User;

public record SignupResponse(
        Long userId,
        String name,
        String email
) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getName(), user.getEmail());
    }
}
