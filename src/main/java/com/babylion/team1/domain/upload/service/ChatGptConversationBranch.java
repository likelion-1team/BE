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
import tools.jackson.databind.JsonNode;

final class ChatGptConversationBranch {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private ChatGptConversationBranch() {
    }

    static List<Turn> restore(JsonNode conversation) {
        JsonNode mapping = conversation.path("mapping");
        String nodeId = conversation.path("current_node").asString(null);
        if (!mapping.isObject() || nodeId == null || nodeId.isBlank()) {
            throw invalidBranch();
        }
        var seen = new HashSet<String>();
        var turns = new ArrayList<Turn>();
        while (nodeId != null) {
            JsonNode node = mapping.path(nodeId);
            if (!node.isObject() || !seen.add(nodeId)) {
                throw invalidBranch();
            }
            JsonNode parent = mapping.path(node.path("parent").asString(""));
            var message = node.path("message");
            var role = roleOf(message.path("author").path("role").asString());
            if (role != null && !message.path("metadata").path("is_visually_hidden_from_conversation").asBoolean(false)) {
                var textParts = new ArrayList<String>();
                for (JsonNode part : message.path("content").path("parts")) {
                    if (part.isString() && !part.asString().isBlank()) {
                        textParts.add(part.asString());
                    }
                }
                if (!textParts.isEmpty()) {
                    turns.add(new Turn(role, String.join("\n", textParts), timestamp(message.path("create_time")),
                            message.path("metadata").path("model_slug").asString(null),
                            node.path("children").size() > 1 || parent.path("children").size() > 1));
                }
            }
            nodeId = node.path("parent").asString(null);
        }
        Collections.reverse(turns);
        return List.copyOf(turns);
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

    record Turn(MessageRole role, String content, LocalDateTime createdAt, String model, boolean branchPoint) {
    }
}
