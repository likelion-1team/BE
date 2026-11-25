package com.babylion.team1.domain.feedback.service;

import com.babylion.team1.domain.feedback.dto.response.FeedbackDetailResponse;
import com.babylion.team1.domain.feedback.dto.response.FeedbackReactionResponse;
import com.babylion.team1.domain.feedback.dto.response.FeedbackShareResponse;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.FeedbackReaction;
import com.babylion.team1.domain.feedback.entity.ReactionType;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackReactionRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.domain.feedback.repository.SharePreviewRepository;
import java.time.Instant;
import com.babylion.team1.domain.feedback.entity.SharedCase;
import com.babylion.team1.domain.feedback.repository.SharedCaseRepository;
import com.babylion.team1.domain.feedback.repository.GuideRepository;
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
    private final FeedbackReactionRepository reactionRepository;
    private final SharePreviewRepository previews;
    private final SharedCaseRepository sharedCases;
    private final GuideRepository guides;

    @Transactional(readOnly = true)
    public FeedbackDetailResponse getDetail(Long feedbackId, Long userId) {
        var feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_NOT_FOUND));
        requireOwner(feedback, userId);
        return FeedbackDetailResponse.from(feedback, evidenceRepository.findByFeedbackIdOrderByIdAsc(feedbackId));
    }

    @Transactional
    public FeedbackShareResponse updateShare(Long feedbackId, Long userId, boolean shared,
                                              Long previewId, Integer previewVersion) {
        Feedback feedback = lockOwnedFeedback(feedbackId, userId);
        if (shared) {
            if (previewId == null || previewVersion == null || previewId < 1 || previewVersion < 1) {
                throw new CustomException(ErrorCode.VALIDATION_FAILED);
            }
            var preview = previews.findById(previewId).filter(row -> row.getUser().getId().equals(userId)
                            && row.getFeedback().getId().equals(feedbackId))
                    .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
            if (preview.getPreviewVersion() != previewVersion) {
                throw new CustomException(ErrorCode.VERSION_CONFLICT);
            }
            Instant now = Instant.now();
            if (preview.getConsumedAt() != null || !preview.getExpiresAt().isAfter(now)) {
                throw new CustomException(ErrorCode.INVALID_STATE);
            }
            if (!evidenceRepository.existsByFeedbackId(feedbackId) || evidenceRepository.hasDeletedOriginal(feedbackId)) {
                throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            revokeActiveCase(feedbackId, now);
            // An existing guide must be designated again for the newly approved snapshot.
            feedback.unshare();
            sharedCases.save(new SharedCase(feedback, preview, now));
            feedback.share();
            preview.consume(now);
        } else {
            revokeActiveCase(feedbackId, Instant.now());
            feedback.unshare();
        }
        return new FeedbackShareResponse(feedback.getIsShared());
    }

    private void revokeActiveCase(Long feedbackId, Instant now) {
        sharedCases.findByFeedbackIdAndStatus(feedbackId, SharedCase.Status.ACTIVE).ifPresent(active -> {
            guides.deleteBySharedCaseId(active.getId());
            active.revoke(now);
        });
    }

    @Transactional
    public FeedbackReactionResponse recordReaction(Long feedbackId, Long userId, ReactionType type, String reason) {
        Feedback feedback = lockOwnedFeedback(feedbackId, userId);
        FeedbackReaction reaction = reactionRepository.findByFeedbackIdAndUserId(feedbackId, userId)
                .orElseGet(() -> FeedbackReaction.builder()
                        .feedback(feedback)
                        .user(feedback.getUser())
                        .reaction(type)
                        .reason(reason)
                        .build());
        reaction.update(type, reason);
        return FeedbackReactionResponse.from(reactionRepository.saveAndFlush(reaction));
    }

    @Transactional
    public void cancelReaction(Long feedbackId, Long userId) {
        feedbackRepository.findByIdForUpdate(feedbackId)
                .filter(feedback -> feedback.isOwnedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (evidenceRepository.hasDeletedOriginal(feedbackId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        reactionRepository.findByFeedbackIdAndUserId(feedbackId, userId)
                .ifPresent(reactionRepository::delete);
    }

    private Feedback lockOwnedFeedback(Long feedbackId, Long userId) {
        Feedback feedback = feedbackRepository.findByIdForUpdate(feedbackId)
                .orElseThrow(() -> new CustomException(ErrorCode.FEEDBACK_NOT_FOUND));
        requireOwner(feedback, userId);
        return feedback;
    }

    private void requireOwner(Feedback feedback, Long userId) {
        if (!feedback.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.NOT_FEEDBACK_OWNER);
        }
    }
}
