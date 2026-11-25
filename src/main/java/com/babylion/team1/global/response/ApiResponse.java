package com.babylion.team1.global.response;

import lombok.Getter;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Getter
public class ApiResponse<T> {
    private final String code;
    private final String message;
    private final T data;
    private final Object errors;
    private final String timestamp;

    private ApiResponse(String code, String message, T data, Object errors) {
        this(code, message, data, errors, ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }

    private ApiResponse(String code, String message, T data, Object errors, OffsetDateTime timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.errors = errors;
        this.timestamp = timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(code, message, data, null);
    }

    public static <T> ApiResponse<T> success(String code, String message, T data, OffsetDateTime timestamp) {
        return new ApiResponse<>(code, message, data, null, timestamp);
    }

    public static <T> ApiResponse<T> error(String code, String message, Object errors) {
        return new ApiResponse<>(code, message, null, errors);
    }
}
