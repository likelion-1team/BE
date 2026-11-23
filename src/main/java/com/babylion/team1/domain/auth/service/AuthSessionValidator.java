package com.babylion.team1.domain.auth.service;

import com.babylion.team1.domain.auth.repository.AuthSessionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 액세스 토큰이 만료 전이어도 로그아웃 등으로 세션이 폐기됐는지 확인한다. */
@Component
@RequiredArgsConstructor
public class AuthSessionValidator {

    private final AuthSessionRepository authSessionRepository;

    public boolean isActive(String sessionId, Long userId) {
        return sessionId != null && authSessionRepository
                .existsByIdAndUserIdAndRevokedFalseAndExpiresAtAfter(sessionId, userId, LocalDateTime.now());
    }
}
