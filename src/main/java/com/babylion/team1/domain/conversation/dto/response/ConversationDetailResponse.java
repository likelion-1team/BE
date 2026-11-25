package com.babylion.team1.domain.conversation.dto.response;

import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import com.babylion.team1.domain.conversation.entity.Message;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record ConversationDetailResponse(Long conversationId, Long conversationVersionId, String title,
        OffsetDateTime startedAt, List<String> excludedAttachments, List<String> highlightMessageIds,
        List<MessageItem> messages) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static ConversationDetailResponse from(ConversationVersion version, List<Message> messages,
                                                  List<String> highlights) {
        return new ConversationDetailResponse(version.getConversation().getId(), version.getId(), version.getTitle(),
                version.getStartedAt().atZone(KST).toOffsetDateTime(), version.getExcludedAttachments(),
                highlights, messages.stream().map(MessageItem::from).toList());
    }

    public record MessageItem(String messageId, String role, String content, OffsetDateTime createdAt) {
        private static MessageItem from(Message message) {
            return new MessageItem(message.getMessageKey(), message.getRole().name(), message.getContent(),
                    message.getCreatedAt() == null ? null : message.getCreatedAt().atZone(KST).toOffsetDateTime());
        }
    }
}
