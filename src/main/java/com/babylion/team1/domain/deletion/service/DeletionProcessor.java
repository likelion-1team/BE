package com.babylion.team1.domain.deletion.service;

import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.analysis.repository.OutcomeAssessmentRepository;
import com.babylion.team1.domain.analysis.repository.ReworkAssessmentRepository;
import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.conversation.repository.ConversationVersionRepository;
import com.babylion.team1.domain.conversation.repository.MessageRepository;
import com.babylion.team1.domain.deletion.entity.DeletionJobStatus;
import com.babylion.team1.domain.deletion.repository.DeletionJobRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackReactionRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackUsageEventRepository;
import com.babylion.team1.domain.feedback.repository.GuideRepository;
import com.babylion.team1.domain.feedback.repository.SharePreviewRepository;
import com.babylion.team1.domain.feedback.repository.SharedCaseRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 삭제 작업 하나를 처리한다. 여러 번 호출해도 결과가 같다. */
@Slf4j
@Component
@RequiredArgsConstructor
class DeletionProcessor {

    static final String FAILURE_CODE = "DELETION_FAILED";

    private final DeletionJobRepository jobs;
    private final ConversationRepository conversations;
    private final ConversationVersionRepository versions;
    private final MessageRepository messages;
    private final AnalysisItemRepository items;
    private final OutcomeAssessmentRepository outcomes;
    private final ReworkAssessmentRepository reworks;
    private final FeedbackRepository feedbacks;
    private final FeedbackEvidenceRepository evidence;
    private final FeedbackReactionRepository reactions;
    private final FeedbackUsageEventRepository usageEvents;
    private final SharePreviewRepository previews;
    private final SharedCaseRepository sharedCases;
    private final GuideRepository guides;
    private final PlatformTransactionManager transactionManager;

    void process(Long jobId) {
        var transaction = new TransactionTemplate(transactionManager);
        try {
            boolean started = Boolean.TRUE.equals(transaction.execute(status -> start(jobId)));
            if (started) {
                Long versionId = jobs.findVersionId(jobId).orElseThrow();
                transaction.executeWithoutResult(status -> purge(jobId, versionId));
            }
        } catch (RuntimeException exception) {
            log.warn("삭제 작업 {} 처리 실패 ({})", jobId, exception.getClass().getSimpleName());
            transaction.executeWithoutResult(status -> jobs.findByIdForUpdate(jobId)
                    .filter(job -> !job.isCompleted())
                    .ifPresent(job -> job.fail(FAILURE_CODE)));
        }
    }

    private boolean start(Long jobId) {
        var job = jobs.findByIdForUpdate(jobId).orElseThrow();
        if (job.isCompleted()) {
            return false;
        }
        job.start();
        return true;
    }

    private void purge(Long jobId, Long versionId) {
        // 삭제 요청과 같은 순서(버전 → 작업 → 피드백)로 잠근다.
        var version = versions.findByIdForUpdate(versionId).orElseThrow();
        var job = jobs.findByIdForUpdate(jobId).orElseThrow();
        if (job.getStatus() != DeletionJobStatus.RUNNING) {
            return;
        }
        purgeFeedbacks(evidence.findFeedbackIdsByVersionId(versionId));
        outcomes.clearNotesByVersionId(versionId);
        reworks.clearNotesByVersionId(versionId);
        items.clearEvidenceByVersionId(versionId);
        messages.deleteByVersionId(versionId);
        // content_hash는 삭제한 대화의 재등록을 막는 데 쓰여 남긴다.
        version.eraseContent();
        conversations.clearTitleIfAllVersionsDeleted(version.getConversation().getId());
        job.complete(Instant.now());
    }

    /** 공유본은 피드백을 FK로 참조하고 파생 내용을 담고 있어 함께 지운다. */
    private void purgeFeedbacks(List<Long> feedbackIds) {
        if (feedbackIds.isEmpty()) {
            return;
        }
        feedbacks.findAllByIdForUpdate(feedbackIds);
        guides.deleteByFeedbackIds(feedbackIds);
        sharedCases.deleteByFeedbackIds(feedbackIds);
        previews.deleteByFeedbackIds(feedbackIds);
        usageEvents.deleteByFeedbackIds(feedbackIds);
        reactions.deleteByFeedbackIds(feedbackIds);
        evidence.deleteByFeedbackIds(feedbackIds);
        feedbacks.deleteByIds(feedbackIds);
    }
}
