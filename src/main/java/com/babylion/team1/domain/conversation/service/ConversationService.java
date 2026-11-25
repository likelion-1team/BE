package com.babylion.team1.domain.conversation.service;

import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.conversation.entity.Message;
import com.babylion.team1.domain.conversation.dto.response.ConversationDetailResponse;
import com.babylion.team1.domain.conversation.repository.ConversationVersionRepository;
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

    private final ConversationVersionRepository versionRepository;
    private final MessageRepository messageRepository;
    private final FeedbackEvidenceRepository evidenceRepository;
    private final AnalysisItemRepository items;

    @Transactional(readOnly = true)
    public ConversationDetailResponse getDetail(Long conversationVersionId, Long highlightFeedbackId, Long userId) {
        var version = versionRepository.findById(conversationVersionId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        var conversation = version.getConversation();
        if (!conversation.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (version.getDeletedAt() != null) {
            throw new CustomException(ErrorCode.SOURCE_DELETED);
        }

        var indexes = new HashSet<Integer>();
        if (highlightFeedbackId != null) {
            var evidence = evidenceRepository.findByFeedbackIdAndConversationIdAndFeedbackUserId(
                    highlightFeedbackId, conversation.getId(), userId);
            if (evidence.isEmpty() || items.findForFeedback(evidence.get(0).getFeedback().getUpload().getId(),
                    conversation.getId()).filter(item -> item.getVersion().getId().equals(conversationVersionId))
                    .isEmpty()) {
                throw new CustomException(ErrorCode.VALIDATION_FAILED);
            }
            evidence.forEach(row -> indexes.addAll(row.getTurnIndexes()));
        }
        var messages = messageRepository.findByVersionIdOrderByIdxAsc(conversationVersionId);
        var highlights = messages.stream().filter(message -> indexes.contains(message.getIdx()))
                .map(Message::getMessageKey).toList();
        return ConversationDetailResponse.from(version, messages, highlights);
    }
}
