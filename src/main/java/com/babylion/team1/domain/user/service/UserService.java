package com.babylion.team1.domain.user.service;

import com.babylion.team1.domain.user.dto.response.UserMeResponse;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /** 토큰은 유효하지만 사용자가 삭제(탈퇴)된 경우 USER_NOT_FOUND를 던진다. */
    public UserMeResponse getMe(Long userId) {
        return userRepository.findById(userId)
                .map(UserMeResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
