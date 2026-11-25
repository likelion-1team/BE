package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.analysis.entity.Analysis;
import com.babylion.team1.domain.analysis.repository.AnalysisRepository;
import com.babylion.team1.domain.upload.dto.response.UploadStatusResponse;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.domain.upload.dto.response.UploadStatusResponse.CleanupStatus;
import com.babylion.team1.domain.upload.entity.Upload.LifecycleStatus;
import com.babylion.team1.global.policy.ServerPolicy;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
        var upload = uploads.findById(uploadId).filter(row -> row.isUploadedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        var status = upload.lifecycleStatus(LocalDateTime.now());
        if (status == LifecycleStatus.EXPIRED) {
            throw new CustomException(ErrorCode.UPLOAD_EXPIRED);
        }
        var createdAt = upload.getUploadedAt().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        Long analysisId = analyses.findByUploadId(uploadId).map(Analysis::getId).orElse(null);
        return new UploadStatusResponse(uploadId, status, createdAt,
                createdAt.plusMinutes(ServerPolicy.UPLOAD_RETENTION_MINUTES),
                upload.getSettings() == null ? null : upload.getSettings().getVersion(), analysisId,
                upload.isOriginalCleaned() ? CleanupStatus.COMPLETED : CleanupStatus.PENDING);
    }
}
