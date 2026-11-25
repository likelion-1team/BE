package com.babylion.team1.domain.conversation.service;

import com.babylion.team1.domain.analysis.entity.AnalysisItem;
import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.conversation.entity.Message;
import com.babylion.team1.domain.conversation.dto.response.ConversationDetailResponse;
import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.conversation.repository.MessageRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final FeedbackEvidenceRepository evidenceRepository;
    private final AnalysisItemRepository items;

    @Transactional(readOnly = true)
    public ConversationDetailResponse getDetail(Long conversationId, Long highlightFeedbackId, Long userId) {
        var conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!conversation.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        var currentVersion = conversation.getCurrentVersion();
        var indexes = new HashSet<Integer>();
        if (highlightFeedbackId != null) {
            var evidence = evidenceRepository.findByFeedbackIdAndConversationIdAndFeedbackUserId(
                    highlightFeedbackId, conversationId, userId);
            if (evidence.isEmpty()) {
                throw new CustomException(ErrorCode.VALIDATION_FAILED);
            }
            var item = items.findForFeedback(evidence.get(0).getFeedback().getUpload().getId(), conversationId);
            if (item.isEmpty() && currentVersion != null && currentVersion.getDeletedAt() != null) {
                throw new CustomException(ErrorCode.SOURCE_DELETED);
            }
            currentVersion = item.map(AnalysisItem::getVersion).orElse(null);
            evidence.forEach(row -> indexes.addAll(row.getTurnIndexes()));
        }
        if (currentVersion != null && currentVersion.getDeletedAt() != null) {
            throw new CustomException(ErrorCode.SOURCE_DELETED);
        }
        var messages = currentVersion == null
                ? messageRepository.findLegacyMessagesOrdered(conversationId)
                : messageRepository.findByVersionIdOrderByIdxAsc(currentVersion.getId());
        var highlights = messages.stream().filter(message -> indexes.contains(message.getIdx()))
                .map(Message::getMessageKey).toList();
        return ConversationDetailResponse.from(conversation, currentVersion, messages, highlights);
    }
}
