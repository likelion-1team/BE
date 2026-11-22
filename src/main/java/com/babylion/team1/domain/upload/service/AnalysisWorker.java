package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.time.LocalDate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@EnableAsync
public class AnalysisWorker {

    private final UploadRepository uploads;
    private final UploadFileStorage storage;
    private final ChatGptExportReader reader;
    private final TransactionTemplate stateTransaction;
    private final ConversationRepository conversations;
    private final ConversationMetrics metrics;
    private final AnalysisFeedbackRules feedbackRules;
    private final GeminiFeedbackClient gemini;
    private final AnalysisPersistence persistence;

    AnalysisWorker(UploadRepository uploads, UploadFileStorage storage, ChatGptExportReader reader,
                   PlatformTransactionManager transactionManager, ConversationRepository conversations,
                   ConversationMetrics metrics, AnalysisFeedbackRules feedbackRules,
                   GeminiFeedbackClient gemini, AnalysisPersistence persistence) {
        this.uploads = uploads;
        this.storage = storage;
        this.reader = reader;
        this.conversations = conversations;
        this.metrics = metrics;
        this.feedbackRules = feedbackRules;
        this.gemini = gemini;
        this.persistence = persistence;
        this.stateTransaction = new TransactionTemplate(transactionManager);
        this.stateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Async
    @TransactionalEventListener
    public void run(AnalysisJob job) {
        try {
            if (persistence.complete(job.uploadId(), prepare(job))) {
                storage.deleteUpload(job.uploadId());
            }
        } catch (CustomException e) {
            fail(job.uploadId(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("업로드 {} 분석 실패 ({})", job.uploadId(), e.getClass().getSimpleName());
            fail(job.uploadId(), "분석 중 오류가 발생했습니다.");
        }
    }

    private List<AnalysisPersistence.Prepared> prepare(AnalysisJob job) {
        var file = storage.pathFor(job.uploadId());
        var period = job.targetPeriod();
        boolean zipped = job.fileName().toLowerCase(Locale.ROOT).endsWith(".zip");
        var selections = new LinkedHashMap<String, Long>();
        job.selections().forEach(selection -> selections.put(selection.externalId(), selection.taskTypeId()));

        // A retry includes conversations already committed by earlier processing of this upload.
        stateTransaction.executeWithoutResult(status -> conversations.findByUploadId(job.uploadId()).forEach(saved ->
                selections.put(saved.getExternalId(), saved.getTaskType().getId())));
        var candidates = reader.read(file, zipped, period.from(), period.to()).stream()
                .filter(candidate -> selections.containsKey(candidate.externalId()))
                .collect(Collectors.toMap(ChatGptExportReader.ExportConversation::externalId, candidate -> candidate));
        if (!candidates.keySet().containsAll(selections.keySet())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "분석 대상 기간의 업로드 후보만 선택할 수 있습니다.");
        }
        var turns = new LinkedHashMap<String, List<ChatGptConversationBranch.Turn>>();
        reader.readSelected(file, zipped, selections.keySet())
                .forEach((id, content) -> turns.put(id, ChatGptConversationBranch.restore(content)));
        if (!turns.keySet().containsAll(selections.keySet())) {
            throw new CustomException(ErrorCode.INVALID_FILE_FORMAT);
        }
        progress(job.uploadId(), 30);
        var results = new LinkedHashMap<String, ConversationMetrics.Result>();
        turns.forEach((id, branch) -> results.put(id, metrics.calculate(branch)));
        progress(job.uploadId(), 60);

        var generatedByGroup = new HashMap<FeedbackGroup, java.util.Optional<AnalysisPersistence.FeedbackText>>();
        var prepared = new ArrayList<AnalysisPersistence.Prepared>();
        results.forEach((id, result) -> {
            var branch = turns.get(id);
            var candidate = candidates.get(id);
            Long taskId = selections.get(id);
            var suggestions = feedbackRules.detect(result).stream().map(finding -> {
                var key = new FeedbackGroup(taskId, candidate.startedAt().toLocalDate(), finding.type());
                var improved = generatedByGroup.computeIfAbsent(key, ignored -> java.util.Optional.ofNullable(
                        gemini.generate(finding.type(), branch.get(finding.evidence().get(0)).content())));
                String question = branch.get(finding.evidence().get(0)).content();
                String excerpt = question.substring(0, question.offsetByCodePoints(0,
                        Math.min(1_000, question.codePointCount(0, question.length()))));
                return new AnalysisPersistence.Suggestion(finding, improved.orElse(null), excerpt);
            }).toList();
            prepared.add(new AnalysisPersistence.Prepared(candidate, taskId, branch, result, suggestions));
        });
        progress(job.uploadId(), 95);
        return prepared;
    }

    private void progress(Long uploadId, int progress) {
        stateTransaction.executeWithoutResult(status -> uploads.findByIdForUpdate(uploadId)
                .filter(upload -> upload.getStatus() == UploadStatus.ANALYZING)
                .ifPresent(upload -> upload.updateProgress(progress)));
    }

    private record FeedbackGroup(Long taskId, LocalDate date, AnalysisFeedbackRules.Type type) {
    }

    private void fail(Long uploadId, String reason) {
        stateTransaction.executeWithoutResult(status -> uploads.findByIdForUpdate(uploadId)
                .filter(upload -> upload.getStatus() == UploadStatus.ANALYZING)
                .ifPresent(upload -> {
                    storage.markFailed(uploadId);
                    upload.fail(reason);
                }));
    }
}
