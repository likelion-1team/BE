package com.babylion.team1.domain.conversation.service;

import com.babylion.team1.domain.conversation.dto.response.ConversationDetailResponse;
import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.conversation.repository.MessageRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final FeedbackEvidenceRepository evidenceRepository;

    @Transactional(readOnly = true)
    public ConversationDetailResponse getDetail(Long conversationId, Long highlightFeedbackId, Long userId) {
        var conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONVERSATION_NOT_FOUND));
        if (!conversation.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.NOT_CONVERSATION_OWNER);
        }

        var currentVersion = conversation.getCurrentVersion();
        var messages = currentVersion == null
                ? messageRepository.findLegacyMessagesOrdered(conversationId)
                : messageRepository.findByVersionIdOrderByIdxAsc(currentVersion.getId());
        List<Integer> highlightIndexes = highlightFeedbackId == null ? List.of()
                : evidenceRepository.findByFeedbackIdAndConversationIdAndFeedbackUserId(
                        highlightFeedbackId, conversationId, userId).stream()
                        .flatMap(evidence -> evidence.getTurnIndexes().stream())
                        .distinct()
                        .sorted()
                        .toList();
        return ConversationDetailResponse.from(conversation, messages, highlightIndexes);
    }
}
