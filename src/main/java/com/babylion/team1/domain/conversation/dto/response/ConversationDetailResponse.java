package com.babylion.team1.domain.conversation.dto.response;

import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.conversation.entity.Message;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

public record ConversationDetailResponse(
        String title,
        OffsetDateTime startedAt,
        String model,
        List<MessageItem> messages,
        List<Integer> highlightIndexes) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static ConversationDetailResponse from(
            Conversation conversation, List<Message> messages, List<Integer> highlightIndexes) {
        return new ConversationDetailResponse(
                conversation.getTitle(),
                conversation.getStartedAt().atZone(KST).toOffsetDateTime(),
                conversation.getModel(),
                messages.stream().map(MessageItem::from).toList(),
                highlightIndexes);
    }

    public record MessageItem(int idx, String role, String content, boolean isRework) {

        private static MessageItem from(Message message) {
            return new MessageItem(message.getIdx(), message.getRole().name().toLowerCase(Locale.ROOT),
                    message.getContent(), message.getIsRework());
        }
    }
}
