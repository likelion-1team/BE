package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.analysis.entity.Analysis;
import com.babylion.team1.domain.analysis.repository.AnalysisRepository;
import com.babylion.team1.domain.upload.dto.response.UploadStatusResponse;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UploadStatusService {
    private final UploadRepository uploads;
    private final AnalysisRepository analyses;

    @Transactional(readOnly = true)
    public UploadStatusResponse getStatus(Long uploadId, Long userId) {
        var upload = uploads.findById(uploadId)
                .orElseThrow(() -> new CustomException(ErrorCode.UPLOAD_NOT_FOUND));
        if (!upload.isUploadedBy(userId)) {
            throw new CustomException(ErrorCode.NOT_UPLOADER);
        }
        Long analysisId = analyses.findByUploadId(uploadId).map(Analysis::getId).orElse(null);
        return new UploadStatusResponse(upload.lifecycleStatus(LocalDateTime.now()), analysisId);
    }
}
