package com.babylion.team1.domain.feedback.service;

import com.babylion.team1.domain.feedback.dto.response.FeedbackDetailResponse;
import com.babylion.team1.domain.feedback.dto.response.FeedbackShareResponse;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackEvidenceRepository evidenceRepository;

    @Transactional(readOnly = true)
    public FeedbackDetailResponse getDetail(Long feedbackId, Long userId) {
        var feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_NOT_FOUND));
        requireOwner(feedback, userId);
        return FeedbackDetailResponse.from(feedback, evidenceRepository.findByFeedbackIdOrderByIdAsc(feedbackId));
    }

    @Transactional
    public FeedbackShareResponse updateShare(Long feedbackId, Long userId, boolean shared) {
        Feedback feedback = feedbackRepository.findByIdForUpdate(feedbackId)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_NOT_FOUND));
        requireOwner(feedback, userId);
        if (shared) {
            feedback.share();
        } else {
            feedback.unshare();
        }
        return new FeedbackShareResponse(feedback.getIsShared());
    }

    private void requireOwner(Feedback feedback, Long userId) {
        if (!feedback.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.NOT_FEEDBACK_OWNER);
        }
    }
}
