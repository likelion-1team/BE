package com.babylion.team1.domain.upload.service;

import static tools.jackson.core.JsonToken.*;

import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;

@Component
class ChatGptExportReader {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final BigDecimal MAX_EPOCH_SECONDS = BigDecimal.valueOf(
            LocalDate.of(9999, 12, 31).plusDays(1).atStartOfDay(KST).toEpochSecond());
    private static final BigDecimal ONE_NANOSECOND = BigDecimal.valueOf(1, 9);

    private final JsonFactory jsonFactory = new JsonFactory();
    private final ObjectMapper mapper = new ObjectMapper();
    private final long maxExpandedBytes;

    ChatGptExportReader(@Value("${app.upload.max-expanded-size}") DataSize maxExpandedSize) {
        this.maxExpandedBytes = maxExpandedSize.toBytes();
    }

    List<ExportConversation> read(Path file, boolean zipped, LocalDate from, LocalDate to) {
        return readFile(file, zipped, input -> readJson(input, from, to, false)).candidates();
    }

    CandidateExport readCandidates(Path file, boolean zipped, LocalDate from, LocalDate to) {
        return readFile(file, zipped, input -> readJson(input, from, to, true));
    }

    Map<String, JsonNode> readSelected(Path file, boolean zipped, Set<String> selectedIds) {
        return readFile(file, zipped, input -> {
            Map<String, JsonNode> selected = new LinkedHashMap<>();
            try (JsonParser parser = mapper.createParser(input)) {
                if (parser.nextToken() != START_ARRAY) {
                    throw invalidFormat();
                }
                while (parser.nextToken() != END_ARRAY) {
                    if (!parser.hasToken(START_OBJECT)) {
                        throw invalidFormat();
                    }
                    JsonNode conversation = mapper.reader().without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readTree(parser);
                    JsonNode id = conversation.path("id");
                    String externalId = id.isNull() || id.isMissingNode()
                            ? conversation.path("conversation_id").asString() : id.asString();
                    if (selectedIds.contains(externalId)) {
                        selected.putIfAbsent(externalId, conversation);
                    }
                }
                if (parser.nextToken() != null) {
                    throw invalidFormat();
                }
            }
            return selected;
        });
    }

    private <T> T readFile(Path file, boolean zipped, Function<InputStream, T> read) {
        try {
            if (zipped) {
                return readZip(file, read);
            }
            try (InputStream input = Files.newInputStream(file)) {
                return read.apply(input);
            }
        } catch (java.util.zip.ZipException e) {
            throw new CustomException(ErrorCode.UNSAFE_ARCHIVE);
        } catch (JacksonException | IOException e) {
            // 파서 예외 메시지에 포함될 수 있는 대화 원문을 응답이나 로그에 남기지 않는다.
            throw invalidFormat();
        }
    }

    private <T> T readZip(Path file, Function<InputStream, T> read) throws IOException {
        try (ZipFile archive = new ZipFile(file.toFile())) {
            validateArchive(archive);
            List<? extends ZipEntry> entries = archive.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().equals("conversations.json")
                            || entry.getName().endsWith("/conversations.json"))
                    .limit(2)
                    .toList();
            if (entries.size() != 1) {
                throw invalidFormat();
            }
            ZipEntry entry = entries.get(0);
            if (entry.getSize() > maxExpandedBytes) {
                throw new CustomException(ErrorCode.EXPANDED_SIZE_EXCEEDED);
            }
            try (CheckedInputStream input = new CheckedInputStream(
                    new LimitedInputStream(archive.getInputStream(entry), maxExpandedBytes), new CRC32())) {
                T conversations = read.apply(input);
                if (input.getChecksum().getValue() != entry.getCrc()) {
                    throw invalidFormat();
                }
                return conversations;
            }
        }
    }

    private void validateArchive(ZipFile archive) {
        if (archive.size() > 1_000) {
            throw new CustomException(ErrorCode.EXPANDED_SIZE_EXCEEDED);
        }
        long expanded = 0;
        for (var entries = archive.entries(); entries.hasMoreElements();) {
            var entry = entries.nextElement();
            String name = entry.getName().replace('\\', '/');
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")
                    || java.util.Arrays.asList(name.split("/")).contains("..")
                    || lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".rar")) {
                throw new CustomException(ErrorCode.UNSAFE_ARCHIVE);
            }
            long size = entry.getSize();
            long compressed = entry.getCompressedSize();
            if (size > maxExpandedBytes - expanded || size > compressed * 100) {
                throw new CustomException(ErrorCode.EXPANDED_SIZE_EXCEEDED);
            }
            expanded += size;
        }
    }

    private CandidateExport readJson(InputStream input, LocalDate from, LocalDate to, boolean hashContent) {
        Map<String, ExportConversation> candidates = new LinkedHashMap<>();
        int conversationCount = 0;
        try (JsonParser parser = jsonFactory.createParser(ObjectReadContext.empty(), input)) {
            if (parser.nextToken() != START_ARRAY) {
                throw invalidFormat();
            }
            while (parser.nextToken() != END_ARRAY) {
                if (!parser.hasToken(START_OBJECT)) {
                    throw invalidFormat();
                }
                conversationCount++;
                ExportConversation conversation = readConversation(parser, hashContent);
                LocalDate date = conversation.startedAt().toLocalDate();
                if (!date.isBefore(from) && !date.isAfter(to)) {
                    candidates.putIfAbsent(conversation.externalId(), conversation);
                }
            }
            if (parser.nextToken() != null) {
                throw invalidFormat();
            }
        }
        return new CandidateExport(conversationCount, List.copyOf(candidates.values()));
    }

    private ExportConversation readConversation(JsonParser parser, boolean hashContent) {
        String id = null;
        String conversationId = null;
        String title = null;
        OffsetDateTime startedAt = null;
        boolean hasMapping = false;
        JsonNode mapping = null;
        String currentNode = null;
        while (parser.nextToken() != END_OBJECT) {
            if (!parser.hasToken(PROPERTY_NAME)) {
                throw invalidFormat();
            }
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "id" -> id = readText(parser);
                case "conversation_id" -> conversationId = readText(parser);
                case "title" -> title = readText(parser);
                case "create_time" -> startedAt = readStartedAt(parser);
                case "current_node" -> {
                    if (hashContent && parser.hasToken(VALUE_STRING)) {
                        currentNode = parser.getString();
                    }
                    parser.skipChildren();
                }
                case "mapping" -> {
                    hasMapping = parser.hasToken(START_OBJECT);
                    if (hashContent && hasMapping) {
                        mapping = mapper.reader().without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                                .readTree(parser);
                    } else {
                        parser.skipChildren();
                    }
                }
                default -> parser.skipChildren();
            }
        }
        String externalId = id != null ? id : conversationId;
        if (externalId == null || externalId.isBlank() || externalId.length() > 100
                || startedAt == null || !hasMapping) {
            throw invalidFormat();
        }
        if (!hashContent) {
            return new ExportConversation(externalId, title, startedAt);
        }

        var conversation = mapper.createObjectNode();
        conversation.set("mapping", mapping);
        conversation.put("current_node", currentNode);
        ChatGptConversationBranch.RestoredConversation restored;
        try {
            restored = ChatGptConversationBranch.restoreWithAttachments(conversation);
        } catch (CustomException e) {
            restored = new ChatGptConversationBranch.RestoredConversation(List.of(), List.of());
        }
        String validationCode = restored.turns().isEmpty() ? "UNSUPPORTED_STRUCTURE" : null;
        return new ExportConversation(externalId, title, startedAt, contentHash(restored), validationCode);
    }

    private String contentHash(ChatGptConversationBranch.RestoredConversation restored) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (var turn : restored.turns()) {
            byte[] content = turn.content().getBytes(StandardCharsets.UTF_8);
            digest.update((turn.role().name() + ":" + content.length + ":").getBytes(StandardCharsets.UTF_8));
            digest.update(content);
        }
        for (String attachment : restored.excludedAttachments()) {
            byte[] label = attachment.getBytes(StandardCharsets.UTF_8);
            digest.update(("ATTACHMENT:" + label.length + ":").getBytes(StandardCharsets.UTF_8));
            digest.update(label);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String readText(JsonParser parser) {
        if (parser.hasToken(VALUE_NULL)) {
            return null;
        }
        if (!parser.hasToken(VALUE_STRING)) {
            throw invalidFormat();
        }
        return parser.getString();
    }

    private OffsetDateTime readStartedAt(JsonParser parser) {
        if (!parser.hasToken(VALUE_NUMBER_INT) && !parser.hasToken(VALUE_NUMBER_FLOAT)) {
            throw invalidFormat();
        }
        BigDecimal time = parser.getDecimalValue();
        if (time.signum() < 0 || time.compareTo(MAX_EPOCH_SECONDS) >= 0) {
            throw invalidFormat();
        }
        BigDecimal exactTime = time.compareTo(ONE_NANOSECOND) < 0
                ? BigDecimal.ZERO
                : time.setScale(9, RoundingMode.DOWN);
        long seconds = exactTime.longValue();
        long nanos = exactTime.subtract(BigDecimal.valueOf(seconds)).movePointRight(9).longValueExact();
        return Instant.ofEpochSecond(seconds, nanos).atZone(KST).toOffsetDateTime();
    }

    private static CustomException invalidFormat() {
        return new CustomException(ErrorCode.UNSUPPORTED_STRUCTURE);
    }

    record CandidateExport(int conversationCount, List<ExportConversation> candidates) {}

    record ExportConversation(String externalId, String title, OffsetDateTime startedAt,
                              String contentHash, String validationCode) {
        ExportConversation(String externalId, String title, OffsetDateTime startedAt) {
            this(externalId, title, startedAt, null, null);
        }
    }

    private static class LimitedInputStream extends FilterInputStream {

        private long remaining;

        LimitedInputStream(InputStream input, long maximum) {
            super(input);
            this.remaining = maximum;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = in.read(bytes, offset, (int) Math.min(length, remaining + 1));
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(int bytes) {
            remaining -= bytes;
            if (remaining < 0) {
                throw new CustomException(ErrorCode.EXPANDED_SIZE_EXCEEDED);
            }
        }
    }
}
