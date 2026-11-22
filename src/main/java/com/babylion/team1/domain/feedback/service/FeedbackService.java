package com.babylion.team1.domain.feedback.service;

import com.babylion.team1.domain.feedback.dto.response.FeedbackDetailResponse;
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
        if (!feedback.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.NOT_FEEDBACK_OWNER);
        }
        return FeedbackDetailResponse.from(feedback, evidenceRepository.findByFeedbackIdOrderByIdAsc(feedbackId));
    }
}
