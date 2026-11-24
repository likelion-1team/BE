package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.analysis.entity.AnalysisItemStatus;
import com.babylion.team1.domain.analysis.entity.ModelCall;
import com.babylion.team1.domain.analysis.repository.ModelCallRepository;
import com.babylion.team1.domain.analysis.entity.AnalysisStatus;
import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.analysis.repository.AnalysisRepository;
import com.babylion.team1.domain.conversation.repository.MessageRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.function.Consumer;
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
    private final ModelCallRepository modelCalls;
    private final TransactionTemplate transaction;

    VersionedAnalysisWorker(AnalysisRepository analyses, AnalysisItemRepository items, MessageRepository messages,
                            TeamRepository teams, ConversationMetrics metrics, AnalysisFeedbackRules rules,
                            GeminiFeedbackClient gemini, AnalysisPersistence persistence, AnalysisPricing pricing,
                            ModelCallRepository modelCalls,
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
        this.modelCalls = modelCalls;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.analysis.worker-delay-ms:1000}")
    void processPending() {
        analyses.findTop20ByStatusInOrderByIdAsc(List.of(AnalysisStatus.QUEUED, AnalysisStatus.RUNNING))
                .forEach(job -> processNext(job.getId()));
    }

    private void processNext(Long analysisId) {
        Input input = transaction.execute(status -> claim(analysisId));
        if (input == null) {
            return;
        }
        try {
            Result result = analyze(input);
            transaction.executeWithoutResult(status -> save(analysisId, input.itemId(), result));
        } catch (RuntimeException exception) {
            String code = exception instanceof GeminiFeedbackClient.ModelFailure
                    ? exception.getMessage() : "ANALYSIS_FAILED";
            log.warn("분석 항목 {} 처리 실패 ({})", input.itemId(), code);
            transaction.executeWithoutResult(status -> {
                var job = analyses.lockById(analysisId).orElseThrow();
                var item = items.findById(input.itemId()).orElseThrow();
                if (item.getStatus() == AnalysisItemStatus.RUNNING) {
                    item.fail(code);
                    job.recordResult(false);
                }
            });
        }
    }

    private Input claim(Long analysisId) {
        var job = analyses.lockById(analysisId).orElseThrow();
        // Do not repeat model calls for claims abandoned by a stopped worker.
        var now = Instant.now();
        for (var expired : items.findByAnalysisIdAndStatusAndClaimExpiresAtBefore(
                analysisId, AnalysisItemStatus.RUNNING, now)) {
            expired.fail("WORKER_TIMEOUT");
            job.recordResult(false);
        }
        var item = items.findFirstByAnalysisIdAndStatusOrderByIdAsc(analysisId, AnalysisItemStatus.QUEUED)
                .orElse(null);
        if (item == null) {
            return null;
        }
        job.begin();
        var version = item.getVersion();
        var rows = messages.findByVersionIdOrderByIdxAsc(version.getId());
        item.begin(now.plus(Duration.ofMinutes(15)));
        if (rows.isEmpty() || version.getDeletedAt() != null) {
            item.fail("ANALYSIS_SOURCE_UNAVAILABLE");
            job.recordResult(false);
            return null;
        }
        var turns = rows.stream().map(row -> new ChatGptConversationBranch.Turn(row.getRole(), row.getContent(),
                row.getCreatedAt(), row.getSourceModel(), row.isBranchPoint())).toList();
        String taskName = item.getTaskNameSnapshot();
        String successDefinition = item.getSuccessDefinitionSnapshot();
        if (taskName == null || successDefinition == null) {
            taskName = item.getTaskType().getName();
            successDefinition = item.getTaskType().getSuccessDefinition();
        }
        return new Input(item.getId(), taskName, successDefinition, turns);
    }

    private List<GeminiFeedbackClient.AssessmentMessage> assessmentMessages(
            List<ChatGptConversationBranch.Turn> turns) {
        var transcript = new ArrayList<GeminiFeedbackClient.AssessmentMessage>();
        int budget = 48_000;
        for (int index = 0; index < turns.size() && budget > 0; index++) {
            var turn = turns.get(index);
            String text = turn.content();
            int length = Math.min(budget, Math.min(4_000, text.codePointCount(0, text.length())));
            transcript.add(new GeminiFeedbackClient.AssessmentMessage("m_" + index, turn.role().name(),
                    text.substring(0, text.offsetByCodePoints(0, length))));
            budget -= length;
        }
        return transcript;
    }

    private Consumer<GeminiFeedbackClient.Attempt> audit(Long itemId, ModelCall.Purpose purpose) {
        return attempt -> transaction.executeWithoutResult(status -> {
            var cost = attempt.inputTokens() == null || attempt.outputTokens() == null ? null
                    : pricing.cost(pricing.rate(attempt.model()), attempt.inputTokens(), attempt.outputTokens());
            modelCalls.save(ModelCall.builder().item(items.getReferenceById(itemId)).purpose(purpose)
                    .attempt(modelCalls.countByItemIdAndPurpose(itemId, purpose) + 1)
                    .model(attempt.model()).inputTokens(attempt.inputTokens()).outputTokens(attempt.outputTokens())
                    .cost(cost).latencyMs(attempt.latencyMs()).errorCode(attempt.errorCode())
                    .status(attempt.errorCode() == null ? ModelCall.Status.SUCCEEDED : ModelCall.Status.FAILED).build());
        });
    }

    private Result analyze(Input input) {
        var turns = input.turns();
        var assessment = gemini.assess(input.taskName(), input.successDefinition(), assessmentMessages(turns),
                audit(input.itemId(), ModelCall.Purpose.ASSESSMENT));
        var result = metrics.calculate(turns);
        var feedbackAudit = audit(input.itemId(), ModelCall.Purpose.FEEDBACK);
        var suggestions = rules.detect(result).stream().filter(finding -> !finding.evidence().isEmpty()
                && finding.evidence().stream().allMatch(index -> index >= 0 && index < turns.size()))
                .map(finding -> {
                    String question = turns.get(finding.evidence().get(0)).content();
                    String excerpt = question.substring(0, question.offsetByCodePoints(0,
                            Math.min(1000, question.codePointCount(0, question.length()))));
                    return new AnalysisPersistence.Suggestion(finding, gemini.generate(finding.type(), question, feedbackAudit), excerpt);
                }).toList();

        return new Result(assessment, result, suggestions);
    }

    private void save(Long analysisId, Long itemId, Result output) {
        var job = analyses.lockById(analysisId).orElseThrow();
        // Lock before the first snapshot read so concurrent versions see the latest promotion.
        teams.findByIdForUpdate(job.getTeam().getId()).orElseThrow();
        var item = items.findById(itemId).orElseThrow();
        if (item.getStatus() != AnalysisItemStatus.RUNNING) {
            return;
        }
        var version = item.getVersion();
        if (version.getDeletedAt() != null) {
            item.fail("ANALYSIS_SOURCE_UNAVAILABLE");
            job.recordResult(false);
            return;
        }
        var assessment = output.assessment();
        var result = output.metrics();
        item.recordAssessment(assessment.scope(), assessment.outcome(), assessment.reworkType(),
                assessment.evidenceMessageKeys(), "v4-1");
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
            var rows = messages.findByVersionIdOrderByIdxAsc(version.getId());
            result.requests().forEach(request -> rows.get(request.index()).markRework(request.rework()));
            conversation.promote(version);
            conversation.recordAnalysis(model, result.requests().size(), tokens,
                    pricing.cost(pricing.rate(result.model()), result.input(), result.output()), result.success(),
                    result.requests().stream().anyMatch(ConversationMetrics.Request::rework), result.review(), result.rework());
            persistence.storeVersionFeedback(job.getUpload(), conversation, item.getTaskType().getId(),
                    version.getConversationDate(), output.suggestions());
        }
        version.recordEstimatedTokens(tokens);
        item.succeed(tokens, gemini.model(), promote);
        job.recordResult(true);
    }
    private record Input(Long itemId, String taskName, String successDefinition,
                         List<ChatGptConversationBranch.Turn> turns) {
    }

    private record Result(GeminiFeedbackClient.Assessment assessment, ConversationMetrics.Result metrics,
                          List<AnalysisPersistence.Suggestion> suggestions) {
    }
}
