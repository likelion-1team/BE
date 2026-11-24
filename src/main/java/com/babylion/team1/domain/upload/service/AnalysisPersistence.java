package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.conversation.entity.Message;
import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.conversation.repository.MessageRepository;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.FeedbackEvidence;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackReactionRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamTaskTypeRepository;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AnalysisPersistence {

    private final UploadRepository uploads;
    private final TeamRepository teams;
    private final TeamTaskTypeRepository taskTypes;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final FeedbackRepository feedbacks;
    private final FeedbackEvidenceRepository evidence;
    private final FeedbackReactionRepository reactions;
    private final AnalysisPricing pricing;

    @Transactional
    public boolean complete(Long uploadId, List<Prepared> prepared) {
        Upload upload = uploads.findByIdForUpdate(uploadId).orElseThrow();
        if (upload.getStatus() != UploadStatus.ANALYZING) {
            return false;
        }

        Long teamId = upload.getTeam().getId();
        // Serialize duplicate checks across uploads for this team before creating any conversation.
        teams.findByIdForUpdate(teamId).orElseThrow();
        var groups = new LinkedHashMap<Group, List<Evidence>>();
        int skipped = 0;
        for (Prepared item : prepared) {
            String externalId = item.metadata().externalId();
            var existing = conversations.findByTeamIdAndUserIdAndExternalId(teamId, upload.getUploader().getId(), externalId).orElse(null);
            if (existing != null && !existing.getUpload().getId().equals(uploadId)) {
                skipped++;
                continue;
            }

            Conversation saved = storeConversation(upload, item, existing);
            for (var suggestion : item.suggestions()) {
                var key = new Group(saved.getTaskType().getId(), saved.getConversationDate(), suggestion.finding().type());
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Evidence(saved, suggestion));
            }
        }

        for (Feedback previous : feedbacks.findByUploadId(uploadId)) {
            reactions.deleteByFeedbackId(previous.getId());
            evidence.deleteByFeedbackId(previous.getId());
            feedbacks.delete(previous);
        }
        feedbacks.flush();
        groups.forEach((key, matches) -> storeFeedback(upload, key, matches));
        upload.complete(skipped);
        return true;
    }

    private Conversation storeConversation(Upload upload, Prepared item, Conversation existing) {
        var metrics = item.metrics();
        var metadata = item.metadata();
        var cost = pricing.cost(pricing.rate(metrics.model()), metrics.input(), metrics.output());
        var reworkIndexes = new HashSet<Integer>();
        metrics.requests().stream().filter(ConversationMetrics.Request::rework)
                .forEach(request -> reworkIndexes.add(request.index()));

        Conversation conversation = existing;
        if (conversation == null) {
            conversation = Conversation.builder().upload(upload).team(upload.getTeam()).user(upload.getUploader())
                    .externalId(metadata.externalId()).title(truncate(metadata.title(), 255))
                    .startedAt(metadata.startedAt().toLocalDateTime()).conversationDate(metadata.startedAt().toLocalDate())
                    .taskType(taskTypes.getReferenceById(item.taskTypeId())).build();
        }
        conversation.recordAnalysis(truncate(metrics.model(), 50), metrics.requests().size(),
                boundedInt(metrics.input() + metrics.output()), cost, metrics.success(), !reworkIndexes.isEmpty(),
                metrics.review(), metrics.rework());
        conversation = conversations.save(conversation);

        if (existing == null) {
            var rows = new ArrayList<Message>(item.turns().size());
            for (int index = 0; index < item.turns().size(); index++) {
                var turn = item.turns().get(index);
                rows.add(Message.builder().conversation(conversation).idx(index).role(turn.role())
                        .content(turn.content()).createdAt(turn.createdAt())
                        .isRework(reworkIndexes.contains(index)).build());
            }
            messages.saveAll(rows);
        } else {
            messages.findByConversationId(conversation.getId())
                    .forEach(message -> message.markRework(reworkIndexes.contains(message.getIdx())));
        }
        return conversation;
    }

    void storeVersionFeedback(Upload upload, Conversation conversation, Long taskTypeId, LocalDate date,
                              List<Suggestion> suggestions) {
        var groups = new LinkedHashMap<Group, List<Evidence>>();
        for (Suggestion suggestion : suggestions) {
            var key = new Group(taskTypeId, date, suggestion.finding().type());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Evidence(conversation, suggestion));
        }
        groups.forEach((key, matches) -> storeFeedback(upload, key, matches));
    }

    private void storeFeedback(Upload upload, Group key, List<Evidence> matches) {
        var type = key.type();
        var first = matches.get(0);
        var text = first.suggestion().improved();
        if (text == null) {
            text = new FeedbackText(type.improvement, type.exampleAfter);
        }
        var feedback = feedbacks.save(Feedback.builder().team(upload.getTeam()).upload(upload)
                .user(upload.getUploader()).taskType(taskTypes.getReferenceById(key.taskTypeId())).feedbackDate(key.date())
                .type(type.name()).title(type.title).problem(type.problem).improvement(text.improvement())
                .exampleBefore(first.suggestion().exampleBefore())
                .exampleAfter(text.exampleAfter()).occurrenceCount(matches.size())
                .expectedSavingTokens(boundedInt(matches.stream().mapToLong(match -> match.suggestion().finding().tokens()).sum()))
                .expectedSavingTurns(boundedInt(matches.stream().mapToLong(match -> match.suggestion().finding().turns()).sum()))
                .expectedSavingCost(matches.stream().map(match -> match.suggestion().finding().savingCost())
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .build());
        evidence.saveAll(matches.stream().map(match -> FeedbackEvidence.builder().feedback(feedback)
                .conversation(match.conversation()).turnIndexes(match.suggestion().finding().evidence()).build()).toList());
    }

    private static int boundedInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static String truncate(String text, int length) {
        if (text == null || text.codePointCount(0, text.length()) <= length) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, length));
    }

    record Prepared(ChatGptExportReader.ExportConversation metadata, Long taskTypeId,
                    List<ChatGptConversationBranch.Turn> turns, ConversationMetrics.Result metrics,
                    List<Suggestion> suggestions) {
    }

    record Suggestion(AnalysisFeedbackRules.Finding finding, FeedbackText improved, String exampleBefore) {
    }

    record FeedbackText(String improvement, String exampleAfter) {
    }

    private record Group(Long taskTypeId, LocalDate date, AnalysisFeedbackRules.Type type) {
    }

    private record Evidence(Conversation conversation, Suggestion suggestion) {
    }
}
