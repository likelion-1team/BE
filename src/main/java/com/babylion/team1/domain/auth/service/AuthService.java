package com.babylion.team1.domain.auth.service;

import com.babylion.team1.domain.auth.dto.request.EmailCheckRequest;
import com.babylion.team1.domain.auth.dto.request.LoginRequest;
import com.babylion.team1.domain.auth.dto.request.SignupRequest;
import com.babylion.team1.domain.auth.dto.request.TokenRefreshRequest;
import com.babylion.team1.domain.auth.dto.response.EmailCheckResponse;
import com.babylion.team1.domain.auth.dto.response.LoginResponse;
import com.babylion.team1.domain.auth.dto.response.SignupResponse;
import com.babylion.team1.domain.auth.dto.response.TokenRefreshResponse;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.jwt.JwtProvider;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    /** 영문과 숫자를 각각 하나 이상 포함한 8자 이상 */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*[0-9]).{8,}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!PASSWORD_PATTERN.matcher(request.password()).matches()) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        try {
            // 동시 가입 요청으로 exists 검사를 통과한 경우 unique 제약에서 걸리므로 즉시 flush해서 409로 변환한다.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        return new SignupResponse(user.getId());
    }

    public EmailCheckResponse checkEmail(EmailCheckRequest request) {
        boolean exists = userRepository.existsByEmail(request.email());
        return new EmailCheckResponse(!exists);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 없는 이메일과 틀린 비밀번호를 구분하지 않고 같은 에러로 응답한다.
        User user = userRepository.findByEmail(request.email())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        // 다시 로그인하면 기존 리프레시 토큰을 덮어쓴다(변경 감지로 커밋 시 반영).
        user.updateRefreshToken(refreshToken);

        return LoginResponse.of(accessToken, refreshToken, user);
    }

    @Transactional
    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        Long userId = jwtProvider.getUserIdFromRefreshToken(request.refreshToken());

        // 같은 토큰으로 동시에 요청해도 하나만 성공하도록 행을 잠근 뒤 비교·교체한다.
        // 사용자가 없거나(탈퇴), 로그아웃으로 NULL이거나, 이미 교체된 이전 토큰이면 모두 INVALID_TOKEN이다.
        User user = userRepository.findByIdForUpdate(userId)
                .filter(found -> request.refreshToken().equals(found.getRefreshToken()))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        String newAccessToken = jwtProvider.createAccessToken(user.getId());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId());
        user.updateRefreshToken(newRefreshToken);

        return new TokenRefreshResponse(newAccessToken, newRefreshToken);
    }

    /**
     * 저장된 리프레시 토큰을 삭제(NULL)해 이후 재발급을 막는다.
     * 이미 로그아웃했거나 사용자가 없어도 결과는 같으므로 에러 없이 끝낸다.
     * 이미 발급된 액세스 토큰은 만료 시간까지 유효하다.
     */
    @Transactional
    public void logout(Long userId) {
        userRepository.findById(userId).ifPresent(User::clearRefreshToken);
    }
}
