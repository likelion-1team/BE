package com.babylion.team1.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직에서 던지는 최상위 예외.
 * 사용 예: throw new CustomException(ErrorCode.USER_NOT_FOUND);
 * 메시지를 바꿔야 할 때만 두 번째 생성자를 쓴다. 이 메시지는 클라이언트에게 그대로 응답된다.
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CustomException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
