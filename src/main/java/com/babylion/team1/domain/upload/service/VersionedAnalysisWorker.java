package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.analysis.entity.AnalysisItemStatus;
import com.babylion.team1.domain.analysis.entity.AnalysisStatus;
import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.analysis.repository.AnalysisRepository;
import com.babylion.team1.domain.conversation.repository.MessageRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Slf4j
class VersionedAnalysisWorker {

    private final AnalysisRepository analyses;
    private final AnalysisItemRepository items;
    private final MessageRepository messages;
    private final TeamRepository teams;
    private final ConversationMetrics metrics;
    private final AnalysisFeedbackRules rules;
    private final GeminiFeedbackClient gemini;
    private final AnalysisPersistence persistence;
    private final AnalysisPricing pricing;
    private final TransactionTemplate transaction;

    VersionedAnalysisWorker(AnalysisRepository analyses, AnalysisItemRepository items, MessageRepository messages,
                            TeamRepository teams, ConversationMetrics metrics, AnalysisFeedbackRules rules,
                            GeminiFeedbackClient gemini, AnalysisPersistence persistence, AnalysisPricing pricing,
                            PlatformTransactionManager transactionManager) {
        this.analyses = analyses;
        this.items = items;
        this.messages = messages;
        this.teams = teams;
        this.metrics = metrics;
        this.rules = rules;
        this.gemini = gemini;
        this.persistence = persistence;
        this.pricing = pricing;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.analysis.worker-delay-ms:1000}")
    void processPending() {
        analyses.findTop20ByStatusInOrderByIdAsc(List.of(AnalysisStatus.QUEUED, AnalysisStatus.RUNNING))
                .forEach(job -> processNext(job.getId()));
    }

    private void processNext(Long analysisId) {
        Long itemId = transaction.execute(status -> {
            var job = analyses.lockById(analysisId).orElseThrow();
            job.begin();
            return items.findFirstByAnalysisIdAndStatusOrderByIdAsc(analysisId, AnalysisItemStatus.QUEUED)
                    .map(item -> item.getId()).orElse(null);
        });
        if (itemId == null) {
            return;
        }
        try {
            transaction.executeWithoutResult(status -> analyze(analysisId, itemId));
        } catch (RuntimeException exception) {
            log.warn("분석 항목 {} 처리 실패 ({})", itemId, exception.getClass().getSimpleName());
            transaction.executeWithoutResult(status -> {
                var job = analyses.lockById(analysisId).orElseThrow();
                var item = items.findById(itemId).orElseThrow();
                if (item.getStatus() == AnalysisItemStatus.QUEUED && item.recordFailedAttempt()) {
                    job.recordResult(false);
                }
            });
        }
    }

    private void analyze(Long analysisId, Long itemId) {
        var job = analyses.lockById(analysisId).orElseThrow();
        var item = items.findById(itemId).orElseThrow();
        if (item.getStatus() != AnalysisItemStatus.QUEUED) {
            return;
        }
        item.begin();
        var version = item.getVersion();
        var rows = messages.findByVersionIdOrderByIdxAsc(version.getId());
        if (rows.isEmpty() || version.getDeletedAt() != null) {
            throw new IllegalStateException("분석 원문을 사용할 수 없습니다.");
        }
        var turns = rows.stream().map(row -> new ChatGptConversationBranch.Turn(row.getRole(), row.getContent(),
                row.getCreatedAt(), row.getSourceModel(), row.isBranchPoint())).toList();
        var result = metrics.calculate(turns);
        var suggestions = rules.detect(result).stream().filter(finding -> !finding.evidence().isEmpty()
                && finding.evidence().stream().allMatch(index -> index >= 0 && index < rows.size()))
                .map(finding -> {
                    String question = turns.get(finding.evidence().get(0)).content();
                    String excerpt = question.substring(0, question.offsetByCodePoints(0,
                            Math.min(1000, question.codePointCount(0, question.length()))));
                    return new AnalysisPersistence.Suggestion(finding, gemini.generate(finding.type(), question), excerpt);
                }).toList();

        // Serialize current-version promotion with uploads and other versions from this team.
        teams.findByIdForUpdate(item.getTeam().getId()).orElseThrow();
        var conversation = version.getConversation();
        var previous = conversation.getCurrentVersion();
        boolean promote = previous == null || previous.getInputVersion() < version.getInputVersion();
        int tokens = (int) Math.min(Integer.MAX_VALUE, result.input() + result.output());
        String model = result.model();
        if (model != null && model.length() > 50) {
            model = model.substring(0, 50);
        }
        if (promote) {
            if (previous != null && !Objects.equals(previous.getId(), version.getId())) {
                items.findByVersionIdAndCurrentTrue(previous.getId()).forEach(current -> current.retire());
            }
            result.requests().forEach(request -> rows.get(request.index()).markRework(request.rework()));
            conversation.promote(version);
            conversation.recordAnalysis(model, result.requests().size(), tokens,
                    pricing.cost(pricing.rate(result.model()), result.input(), result.output()), result.success(),
                    result.requests().stream().anyMatch(ConversationMetrics.Request::rework), result.review(), result.rework());
            persistence.storeVersionFeedback(job.getUpload(), conversation, item.getTaskType().getId(),
                    version.getConversationDate(), suggestions);
        }
        version.recordEstimatedTokens(tokens);
        item.succeed(tokens, model, promote);
        job.recordResult(true);
    }
}
