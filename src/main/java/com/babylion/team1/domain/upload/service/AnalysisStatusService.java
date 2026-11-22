package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.upload.dto.response.AnalysisStatusResponse;
import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisStatusService {

    private final UploadRepository uploads;
    private final ConversationRepository conversations;

    @Transactional(readOnly = true)
    public AnalysisStatusResponse getStatus(Long uploadId, Long userId) {
        var upload = uploads.findById(uploadId)
                .orElseThrow(() -> new CustomException(ErrorCode.UPLOAD_NOT_FOUND));
        if (!upload.isUploadedBy(userId)) {
            throw new CustomException(ErrorCode.NOT_UPLOADER);
        }

        var status = upload.getStatus();
        if (status == UploadStatus.DONE) {
            return new AnalysisStatusResponse(status, upload.getProgress(), null,
                    conversations.countByUploadId(uploadId), upload.getDuplicateCount());
        }
        return new AnalysisStatusResponse(status, upload.getProgress(), upload.getFailReason(), null, null);
    }
}
