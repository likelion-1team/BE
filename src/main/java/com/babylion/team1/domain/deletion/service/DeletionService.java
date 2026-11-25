package com.babylion.team1.domain.deletion.service;

import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.analysis.repository.AnalysisRepository;
import com.babylion.team1.domain.conversation.repository.ConversationVersionRepository;
import com.babylion.team1.domain.deletion.dto.response.DeletionJobResponse;
import com.babylion.team1.domain.deletion.dto.response.DeletionRequestResponse;
import com.babylion.team1.domain.deletion.entity.DeletionJob;
import com.babylion.team1.domain.deletion.repository.DeletionJobRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.domain.feedback.repository.GuideRepository;
import com.babylion.team1.domain.feedback.repository.SharedCaseRepository;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class DeletionService {

    private final DeletionJobRepository jobs;
    private final ConversationVersionRepository versions;
    private final AnalysisRepository analyses;
    private final AnalysisItemRepository items;
    private final FeedbackRepository feedbacks;
    private final FeedbackEvidenceRepository evidence;
    private final SharedCaseRepository sharedCases;
    private final GuideRepository guides;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final PlatformTransactionManager transactionManager;

    /** 접근 차단까지 커밋한 뒤 돌아온다. 실제 삭제는 {@link DeletionWorker}가 처리한다. */
    public DeletionRequestResponse requestDeletion(Long versionId, Long userId) {
        if (!versions.existsByIdAndConversationUserId(versionId, userId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        // 트랜잭션의 첫 일반 조회가 잠금 뒤에 일어나도록 잠글 ID는 미리 읽는다.
        var analysisIds = items.findAnalysisIdsByVersionId(versionId);
        return new TransactionTemplate(transactionManager).execute(status ->
                accept(versionId, userId, analysisIds));
    }

    private DeletionRequestResponse accept(Long versionId, Long userId, List<Long> analysisIds) {
        // 분석 워커의 결과 저장과 같은 행을 먼저 잠가, 삭제한 버전에 뒤늦게 피드백이 생기지 않게 한다.
        analysisIds.forEach(id -> analyses.lockById(id).orElseThrow());
        var version = versions.findByIdForUpdate(versionId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        var existing = jobs.findIdsByVersionId(versionId, Limit.of(1)).stream().findFirst();
        if (existing.isPresent()) {
            var job = jobs.findByIdForUpdate(existing.get()).orElseThrow();
            if (job.requeueIfFailed()) {
                events.publishEvent(new DeletionRequestedEvent(job.getId()));
            }
            return DeletionRequestResponse.from(job);
        }

        var now = Instant.now();
        version.markDeleted(now);
        var job = jobs.save(new DeletionJob(version, users.getReferenceById(userId), now));
        blockAccess(versionId);
        events.publishEvent(new DeletionRequestedEvent(job.getId()));
        return DeletionRequestResponse.from(job);
    }

    private void blockAccess(Long versionId) {
        items.retireByVersionId(versionId);
        var feedbackIds = evidence.findFeedbackIdsByVersionId(versionId);
        if (feedbackIds.isEmpty()) {
            return;
        }
        // 공유 승인·가이드 지정도 피드백 행을 잠그므로 경합해도 공개 상태로 남지 않는다.
        feedbacks.findAllByIdForUpdate(feedbackIds);
        guides.deleteByFeedbackIds(feedbackIds);
        sharedCases.markDeletedByFeedbackIds(feedbackIds);
    }

    @Transactional(readOnly = true)
    public DeletionJobResponse getJob(Long jobId, Long userId) {
        return jobs.findByIdAndUserId(jobId, userId).map(DeletionJobResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
