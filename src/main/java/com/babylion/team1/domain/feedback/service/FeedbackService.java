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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import com.babylion.team1.domain.analysis.dto.AnalysisItemsResponse.OutcomeAssessment;
import com.babylion.team1.domain.analysis.dto.AnalysisItemsResponse.ReworkAssessment;
import com.babylion.team1.domain.analysis.dto.OutcomeAssessmentResponse;
import com.babylion.team1.domain.analysis.dto.ReworkAssessmentResponse;
import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.analysis.repository.OutcomeAssessmentRepository;
import com.babylion.team1.domain.analysis.repository.ReworkAssessmentRepository;
import com.babylion.team1.domain.analysis.service.AnalysisQueryService;
import com.babylion.team1.domain.conversation.entity.Message;
import com.babylion.team1.domain.conversation.repository.MessageRepository;
import com.babylion.team1.domain.feedback.dto.response.FeedbackExample;
import com.babylion.team1.domain.feedback.entity.FeedbackUsageEvent;
import com.babylion.team1.domain.feedback.repository.FeedbackUsageEventRepository;
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
    private final AnalysisItemRepository items;
    private final MessageRepository messages;
    private final OutcomeAssessmentRepository outcomes;
    private final ReworkAssessmentRepository reworks;
    private final FeedbackUsageEventRepository usage;

    @Transactional(readOnly = true)
    public FeedbackDetailResponse getDetail(Long feedbackId, Long userId) {
        var feedback = feedbackRepository.findById(feedbackId).filter(row -> row.isOwnedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (evidenceRepository.hasDeletedOriginal(feedbackId)) {
            throw new CustomException(ErrorCode.SOURCE_DELETED);
        }
        var evidenceRows = evidenceRepository.findByFeedbackIdOrderByIdAsc(feedbackId);
        var evidence = evidenceRows.stream().findFirst().orElse(null);
        var conversation = evidence == null ? null : evidence.getConversation();
        var item = conversation == null ? null
                : items.findForFeedback(feedback.getUpload().getId(), conversation.getId()).orElse(null);
        var version = item == null ? null : item.getVersion();
        if (version != null && version.getDeletedAt() != null) {
            throw new CustomException(ErrorCode.SOURCE_DELETED);
        }
        var original = conversation == null ? List.<Message>of() : version == null
                ? messages.findLegacyMessagesOrdered(conversation.getId())
                : messages.findByVersionIdOrderByIdxAsc(version.getId());
        var indexes = evidenceRows.stream()
                .filter(row -> row.getConversation().getId().equals(conversation.getId()))
                .flatMap(row -> row.getTurnIndexes().stream()).collect(java.util.stream.Collectors.toSet());
        var messageIds = original.stream().filter(message -> indexes.contains(message.getIdx()))
                .map(Message::getMessageKey).distinct().toList();
        Long itemId = item == null ? null : item.getId();
        var outcome = itemId == null ? null : outcomes.findLatestByItemIds(List.of(itemId)).stream()
                .findFirst().map(OutcomeAssessmentResponse::from)
                .map(row -> new OutcomeAssessment(row.version(), row.outcome().name(), row.source(), row.assessedAt()))
                .orElse(null);
        var rework = itemId == null ? null : reworks.findLatestByItemIds(List.of(itemId)).stream()
                .findFirst().map(ReworkAssessmentResponse::from)
                .map(row -> new ReworkAssessment(row.version(), row.type().name(), row.source(), row.assessedAt()))
                .orElse(null);
        var sharedCase = sharedCases.findByFeedbackIdAndStatus(feedbackId, SharedCase.Status.ACTIVE).orElse(null);
        var sharing = new FeedbackDetailResponse.Sharing(sharedCase == null ? "NOT_SHARED" : "SHARED",
                feedback.getShareVersion(), sharedCase == null ? null : sharedCase.getId());
        var reaction = reactionRepository.findByFeedbackIdAndUserId(feedbackId, userId)
                .map(FeedbackReaction::getReaction).orElse(null);
        return new FeedbackDetailResponse(feedbackId, itemId, conversation == null ? null : conversation.getId(),
                version == null ? null : version.getId(), feedback.getType(), feedback.getTitle(),
                feedback.getProblem(), feedback.getCauses(), feedback.getImprovement(), FeedbackExample.from(feedback),
                messageIds, List.of(), item == null ? null : AnalysisQueryService.assessment(item), outcome, rework,
                reaction, sharing, FeedbackDetailResponse.ExpectedSaving.NOT_MEASURED,
                new FeedbackDetailResponse.UsageSummary(latestUsage(feedbackId, userId, FeedbackUsageEvent.Type.COPY_SUCCEEDED),
                        latestUsage(feedbackId, userId, FeedbackUsageEvent.Type.USE_CONFIRMED)));
    }

    @Transactional
    public FeedbackShareResponse updateShare(Long feedbackId, Long userId, boolean shared,
                                            Integer expectedShareVersion, Long previewId, Integer previewVersion) {
        Feedback feedback = lockOwnedFeedback(feedbackId, userId);
        var active = sharedCases.findByFeedbackIdAndStatus(feedbackId, SharedCase.Status.ACTIVE).orElse(null);
        Instant now = Instant.now();
        if (!shared) {
            if (active != null || Boolean.TRUE.equals(feedback.getIsShared())) {
                revokeActiveCase(active, now);
                feedback.unshare();
                feedback.advanceShareVersion();
            }
            return new FeedbackShareResponse(false, feedback.getShareVersion(), null);
        }
        if (expectedShareVersion == null || expectedShareVersion < 0
                || previewId == null || previewVersion == null || previewId < 1 || previewVersion < 1) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        var preview = previews.findById(previewId).filter(row -> row.getUser().getId().equals(userId)
                        && row.getFeedback().getId().equals(feedbackId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!evidenceRepository.existsByFeedbackId(feedbackId) || evidenceRepository.hasDeletedOriginal(feedbackId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (preview.getPreviewVersion() != previewVersion) {
            throw new CustomException(ErrorCode.VERSION_CONFLICT);
        }
        if (active != null && active.getPreview().getId().equals(previewId)
                && expectedShareVersion == feedback.getShareVersion() - 1) {
            return new FeedbackShareResponse(true, feedback.getShareVersion(), active.getId());
        }
        if (expectedShareVersion != feedback.getShareVersion()) {
            throw new CustomException(ErrorCode.VERSION_CONFLICT);
        }
        if (!preview.getExpiresAt().isAfter(now)) {
            throw new CustomException(ErrorCode.PREVIEW_EXPIRED);
        }
        if (preview.getConsumedAt() != null) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }
        revokeActiveCase(active, now);
        feedback.unshare();
        var saved = sharedCases.save(new SharedCase(feedback, preview, now));
        feedback.share();
        feedback.advanceShareVersion();
        preview.consume(now);
        return new FeedbackShareResponse(true, feedback.getShareVersion(), saved.getId());
    }

    private void revokeActiveCase(SharedCase active, Instant now) {
        if (active != null) {
            guides.deleteBySharedCaseId(active.getId());
            active.revoke(now);
        }
    }

    @Transactional
    public FeedbackReactionResponse recordReaction(Long feedbackId, Long userId, ReactionType type, String reason) {
        Feedback feedback = lockOwnedFeedback(feedbackId, userId);
        if (evidenceRepository.hasDeletedOriginal(feedbackId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
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
        return feedbackRepository.findByIdForUpdate(feedbackId).filter(row -> row.isOwnedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private OffsetDateTime latestUsage(Long feedbackId, Long userId, FeedbackUsageEvent.Type type) {
        return usage.findFirstByFeedbackIdAndUserIdAndEventTypeOrderByRecordedAtDesc(feedbackId, userId, type)
                .map(row -> row.getRecordedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime()).orElse(null);
    }
}
