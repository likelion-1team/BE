package com.babylion.team1.domain.user.dto.response;

import com.babylion.team1.domain.user.entity.User;

public record UserMeResponse(
        Long userId,
        String name,
        String email
) {

    public static UserMeResponse from(User user) {
        return new UserMeResponse(user.getId(), user.getName(), user.getEmail());
    }
}
