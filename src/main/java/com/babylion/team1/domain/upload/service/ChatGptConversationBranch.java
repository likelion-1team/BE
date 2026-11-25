package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.conversation.entity.MessageRole;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import tools.jackson.databind.JsonNode;

final class ChatGptConversationBranch {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private ChatGptConversationBranch() {
    }

    static List<Turn> restore(JsonNode conversation) {
        return restoreWithAttachments(conversation).turns();
    }

    static RestoredConversation restoreWithAttachments(JsonNode conversation) {
        JsonNode mapping = conversation.path("mapping");
        String nodeId = conversation.path("current_node").asString(null);
        if (!mapping.isObject() || nodeId == null || nodeId.isBlank()) {
            throw invalidBranch();
        }
        var seen = new HashSet<String>();
        var turns = new ArrayList<Turn>();
        var attachmentsByTurn = new ArrayList<List<String>>();
        while (nodeId != null) {
            JsonNode node = mapping.path(nodeId);
            if (!node.isObject() || !seen.add(nodeId)) {
                throw invalidBranch();
            }
            JsonNode parent = mapping.path(node.path("parent").asString(""));
            var message = node.path("message");
            var role = roleOf(message.path("author").path("role").asString());
            if (role != null && !message.path("metadata").path("is_visually_hidden_from_conversation").asBoolean(false)) {
                var attachments = new LinkedHashSet<String>();
                for (JsonNode attachment : message.path("metadata").path("attachments")) {
                    attachments.add(attachmentLabel(attachment));
                }
                var textParts = new ArrayList<String>();
                for (JsonNode part : message.path("content").path("parts")) {
                    if (part.isString() && !part.asString().isBlank()) {
                        textParts.add(part.asString());
                    } else if (part.isObject() && part.has("asset_pointer")) {
                        String label = attachmentLabel(part);
                        if (attachments.isEmpty() || !"attachment".equals(label)) {
                            attachments.add(label);
                        }
                    }
                }
                attachmentsByTurn.add(List.copyOf(attachments));
                if (!textParts.isEmpty()) {
                    turns.add(new Turn(role, String.join("\n", textParts), timestamp(message.path("create_time")),
                            message.path("metadata").path("model_slug").asString(null),
                            node.path("children").size() > 1 || parent.path("children").size() > 1));
                }
            }
            nodeId = node.path("parent").asString(null);
        }
        Collections.reverse(turns);
        Collections.reverse(attachmentsByTurn);
        return new RestoredConversation(List.copyOf(turns), attachmentsByTurn.stream()
                .flatMap(List::stream).toList());
    }

    private static String attachmentLabel(JsonNode attachment) {
        String name = attachment.path("name").asString("").strip().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255 || name.codePoints().anyMatch(Character::isISOControl)) {
            return "attachment";
        }
        return name;
    }

    private static MessageRole roleOf(String role) {
        return switch (role) {
            case "user" -> MessageRole.USER;
            case "assistant" -> MessageRole.ASSISTANT;
            default -> null;
        };
    }

    private static LocalDateTime timestamp(JsonNode value) {
        if (!value.isNumber()) {
            return null;
        }
        try {
            var seconds = value.decimalValue();
            long milliseconds = seconds.movePointRight(3).setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), KST);
        } catch (DateTimeException | ArithmeticException e) {
            return null;
        }
    }

    private static CustomException invalidBranch() {
        return new CustomException(ErrorCode.INVALID_FILE_FORMAT, "선택한 대화의 메시지 경로를 복원할 수 없습니다.");
    }

    record RestoredConversation(List<Turn> turns, List<String> excludedAttachments) {
    }

    record Turn(MessageRole role, String content, LocalDateTime createdAt, String model, boolean branchPoint) {
    }
}
