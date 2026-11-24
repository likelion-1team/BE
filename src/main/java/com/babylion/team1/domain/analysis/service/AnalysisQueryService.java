package com.babylion.team1.domain.analysis.service;

import com.babylion.team1.domain.analysis.dto.AnalysisStatusResponse;
import com.babylion.team1.domain.analysis.dto.AnalysisItemsResponse;
import com.babylion.team1.domain.analysis.dto.AnalysisItemsResponse.AiAssessment;
import com.babylion.team1.domain.analysis.dto.AnalysisItemsResponse.Item;
import com.babylion.team1.domain.analysis.dto.AnalysisItemsResponse.OutcomeAssessment;
import com.babylion.team1.domain.analysis.dto.AnalysisItemsResponse.ReworkAssessment;
import com.babylion.team1.domain.analysis.dto.ReworkAssessmentResponse;
import com.babylion.team1.domain.analysis.repository.ReworkAssessmentRepository;
import com.babylion.team1.domain.analysis.dto.OutcomeAssessmentResponse;
import com.babylion.team1.domain.analysis.repository.OutcomeAssessmentRepository;
import com.babylion.team1.domain.analysis.entity.AnalysisItem;
import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import com.babylion.team1.domain.analysis.entity.Analysis;
import com.babylion.team1.domain.analysis.repository.AnalysisRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisQueryService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AnalysisRepository analyses;
    private final AnalysisItemRepository items;
    private final FeedbackEvidenceRepository evidence;
    private final OutcomeAssessmentRepository outcomes;
    private final ReworkAssessmentRepository reworks;

    public AnalysisStatusResponse getStatus(Long analysisId, Long userId) {
        Analysis analysis = requireOwnedAnalysis(analysisId, userId);
        int selected = analysis.getSelectedCount();
        int completed = analysis.getCompletedCount();
        int progress = selected == 0 ? 0 : (int) (100L * completed / selected);
        return new AnalysisStatusResponse(analysisId, analysis.getUpload().getId(),
                analysis.getStatus().name(), analysis.getSettings().getVersion(), inKst(analysis.getCreatedAt()),
                inKst(analysis.getStartedAt()), inKst(analysis.getFinishedAt()), analysis.getStage(),
                selected, completed, analysis.getSucceededCount(), analysis.getFailedCount(), progress,
                3, "/api/v1/analyses/" + analysisId + "/items");
    }

    public AnalysisItemsResponse getItems(Long analysisId, Long userId, int page, int size) {
        Analysis analysis = requireOwnedAnalysis(analysisId, userId);
        if (page < 1 || size < 1 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        var results = items.findByAnalysisIdAndUserIdAndVersionDeletedAtIsNullOrderByIdAsc(
                analysisId, userId, PageRequest.of(page - 1, size));
        if (results.isEmpty()) {
            return new AnalysisItemsResponse(List.of(), page, size, results.getTotalElements());
        }
        var conversationIds = results.stream().map(item -> item.getVersion().getConversation().getId()).distinct().toList();
        var feedbackByConversation = new HashMap<Long, List<Long>>();
        for (var row : evidence.findItemFeedback(analysis.getUpload().getId(), userId, conversationIds)) {
            feedbackByConversation.computeIfAbsent(row.getConversationId(), ignored -> new ArrayList<>())
                    .add(row.getFeedbackId());
        }
        var itemIds = results.stream().map(AnalysisItem::getId).toList();
        var outcomesByItem = new HashMap<Long, OutcomeAssessment>();
        for (var assessment : outcomes.findLatestByItemIds(itemIds)) {
            var outcome = OutcomeAssessmentResponse.from(assessment);
            outcomesByItem.put(outcome.itemId(), new OutcomeAssessment(outcome.version(), outcome.outcome().name(),
                    outcome.source(), outcome.assessedAt()));
        }
        var reworksByItem = new HashMap<Long, ReworkAssessment>();
        for (var assessment : reworks.findLatestByItemIds(itemIds)) {
            var rework = ReworkAssessmentResponse.from(assessment);
            reworksByItem.put(rework.itemId(), new ReworkAssessment(rework.version(), rework.type().name(),
                    rework.source(), rework.assessedAt()));
        }
        var response = results.stream().map(item -> {
            Long conversationId = item.getVersion().getConversation().getId();
            return new Item(item.getId(), conversationId, item.getVersion().getId(), item.getStatus().name(),
                    name(item.getScopeClassification()), item.getFailureCode(),
                    feedbackByConversation.getOrDefault(conversationId, List.of()), assessment(item), outcomesByItem.get(item.getId()), reworksByItem.get(item.getId()));
        }).toList();
        return new AnalysisItemsResponse(response, page, size, results.getTotalElements());
    }

    private static AiAssessment assessment(AnalysisItem item) {
        if (item.getAiOutcome() == null && item.getAiReworkType() == null && item.getAiEvidenceMessageKeys() == null
                && item.getModelVersion() == null && item.getRubricVersion() == null) {
            return null;
        }
        return new AiAssessment(name(item.getAiOutcome()), name(item.getAiReworkType()),
                item.getAiEvidenceMessageKeys() == null ? List.of() : item.getAiEvidenceMessageKeys(),
                item.getModelVersion(), item.getRubricVersion());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private Analysis requireOwnedAnalysis(Long analysisId, Long userId) {
        return analyses.findByIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static OffsetDateTime inKst(Instant instant) {
        return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
    }
}
