package com.babylion.team1.domain.upload.controller;

import com.babylion.team1.domain.upload.dto.response.UploadResponse;
import com.babylion.team1.domain.upload.service.UploadService;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestController
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping(path = "/api/v1/teams/{teamId}/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResponse>> upload(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @RequestPart("file") MultipartFile file) {
        UploadResponse response = uploadService.upload(teamId, userId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("S201", "업로드 성공", response));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingFile(MissingServletRequestPartException e) {
        ErrorCode errorCode = ErrorCode.INVALID_FILE_FORMAT;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), null));
    }
}
