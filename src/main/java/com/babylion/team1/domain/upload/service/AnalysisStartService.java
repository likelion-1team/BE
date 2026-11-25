package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.analysis.entity.Analysis;
import com.babylion.team1.domain.analysis.entity.AnalysisItem;
import com.babylion.team1.domain.analysis.entity.AnalysisItemStatus;
import com.babylion.team1.domain.analysis.entity.AnalysisStatus;
import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.analysis.repository.AnalysisRepository;
import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import com.babylion.team1.domain.conversation.entity.Message;
import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.conversation.repository.ConversationVersionRepository;
import com.babylion.team1.domain.conversation.repository.MessageRepository;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.team.repository.TeamTaskTypeRepository;
import com.babylion.team1.domain.upload.dto.request.AnalysisStartRequest;
import com.babylion.team1.domain.upload.dto.response.AnalysisStartResponse;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.upload.entity.UploadCandidate;
import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.upload.repository.UploadCandidateRepository;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.policy.ServerPolicy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalysisStartService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final EntityManager entityManager;
    private final UploadRepository uploads;
    private final UploadCandidateRepository candidates;
    private final TeamRepository teams;
    private final TeamTaskTypeRepository taskTypes;
    private final TeamSettingsRepository settings;
    private final AnalysisRepository analyses;
    private final AnalysisItemRepository items;
    private final ConversationRepository conversations;
    private final ConversationVersionRepository versions;
    private final MessageRepository messages;
    private final UploadFileStorage storage;
    private final ChatGptExportReader reader;

    private final PlatformTransactionManager transactionManager;

    public AnalysisStartResponse start(Long uploadId, Long userId, String idempotencyKey,
                                       AnalysisStartRequest request) {
        String key = validateKey(idempotencyKey);
        var selections = validateSelections(request);
        String hash = hashRequest(uploadId, request);
        var prepared = prepareSource(uploadId, userId, selections);
        return new TransactionTemplate(transactionManager).execute(status ->
                accept(uploadId, userId, request.settingsVersion(), key, hash, selections, prepared));
    }

    private PreparedSource prepareSource(Long uploadId, Long userId, Map<String, Long> selections) {
        var fileName = uploads.findReadableFileName(uploadId, userId,
                List.of(UploadStatus.UPLOADED, UploadStatus.FAILED));
        if (fileName.isEmpty()) {
            return new PreparedSource(Map.of(), null);
        }
        var selected = candidates.findByUploadIdAndSelectionIdIn(uploadId, selections.keySet());
        if (selected.size() != selections.size() || selected.stream().anyMatch(candidate -> !candidate.isSelectable())) {
            return new PreparedSource(Map.of(), null);
        }
        var externalIds = selected.stream().map(UploadCandidate::getExternalId).collect(Collectors.toSet());
        boolean zipped = fileName.orElseThrow().toLowerCase(Locale.ROOT).endsWith(".zip");
        try {
            var source = reader.readSelected(storage.pathFor(uploadId), zipped, externalIds);
            var turns = source.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    entry -> ChatGptConversationBranch.restoreWithAttachments(entry.getValue())));
            return new PreparedSource(turns, null);
        } catch (CustomException exception) {
            // A concurrent request may have consumed the upload while the file was being read.
            return new PreparedSource(Map.of(), exception);
        }
    }

    private AnalysisStartResponse accept(Long uploadId, Long userId, int settingsVersion, String key, String hash,
                                         Map<String, Long> selections, PreparedSource prepared) {
        // Serialize a user's idempotency keys even when competing requests name different uploads.
        if (entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        var upload = uploads.findByIdForUpdate(uploadId).filter(row -> row.isUploadedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        Long teamId = upload.getTeam().getId();
        teams.findByIdForUpdate(teamId).orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        var previous = analyses.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (previous != null) {
            if (previous.getCreatedAt().plus(Duration.ofHours(24)).isAfter(Instant.now())) {
                if (!previous.getRequestHash().equals(hash)) {
                    throw new CustomException(ErrorCode.IDEMPOTENCY_CONFLICT);
                }
                return response(previous);
            }
            previous.releaseExpiredKey();
            analyses.flush();
        }
        if (upload.getStatus() == UploadStatus.CONSUMED || upload.getStatus() == UploadStatus.DONE
                || upload.getStatus() == UploadStatus.ANALYZING) {
            throw new CustomException(ErrorCode.UPLOAD_CONSUMED);
        }
        var file = storage.pathFor(uploadId);
        if (!Files.isRegularFile(file) || !upload.getUploadedAt()
                .plusMinutes(ServerPolicy.UPLOAD_RETENTION_MINUTES).isAfter(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.UPLOAD_EXPIRED);
        }
        var current = settings.findByTeamId(teamId).orElseThrow(() -> new CustomException(ErrorCode.VERSION_CONFLICT));
        if (upload.getSettings() == null || !current.getId().equals(upload.getSettings().getId())
                || current.getVersion() != settingsVersion) {
            throw new CustomException(ErrorCode.VERSION_CONFLICT);
        }

        var allowedTypes = taskTypes.findByTeamIdAndIsActiveTrueOrderById(teamId).stream()
                .collect(Collectors.toMap(TeamTaskType::getId, Function.identity()));
        var selected = candidates.findByUploadIdAndSelectionIdIn(uploadId, selections.keySet());
        if (selected.size() != selections.size()) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        if (selected.stream().anyMatch(candidate ->
                candidate.getDuplicateType() == UploadCandidate.DuplicateType.SAME_CONTENT)) {
            throw new CustomException(ErrorCode.DUPLICATE_CONTENT);
        }
        if (selected.stream().anyMatch(candidate ->
                !candidate.isSelectable() || !allowedTypes.containsKey(selections.get(candidate.getSelectionId())))) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        if (prepared.error() != null) {
            throw prepared.error();
        }
        if (selected.stream().anyMatch(candidate -> !prepared.conversations().containsKey(candidate.getExternalId()))) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }

        var analysis = analyses.save(Analysis.builder().upload(upload).team(upload.getTeam())
                .user(upload.getUploader()).settings(current).idempotencyKey(key).requestHash(hash)
                .selectedCount(selected.size()).build());
        for (UploadCandidate candidate : selected) {
            var taskType = allowedTypes.get(selections.get(candidate.getSelectionId()));
            var source = prepared.conversations().get(candidate.getExternalId());
            var version = saveVersion(upload, candidate, taskType, source);
            items.save(AnalysisItem.builder().analysis(analysis).version(version).team(upload.getTeam())
                    .user(upload.getUploader()).taskType(taskType).taskNameSnapshot(taskType.getName())
                    .successDefinitionSnapshot(taskType.getSuccessDefinition())
                    .conversationDate(version.getConversationDate()).build());
        }
        upload.consume();
        return response(analysis);
    }

    private ConversationVersion saveVersion(Upload upload, UploadCandidate candidate, TeamTaskType taskType,
                                             ChatGptConversationBranch.RestoredConversation source) {
        var turns = source.turns();
        var date = candidate.getStartedAt().toLocalDate();
        var conversation = conversations.findByTeamIdAndUserIdAndExternalId(upload.getTeam().getId(),
                upload.getUploader().getId(), candidate.getExternalId()).orElseGet(() ->
                conversations.save(Conversation.builder().upload(upload).team(upload.getTeam())
                        .user(upload.getUploader()).taskType(taskType).externalId(candidate.getExternalId())
                        .title(candidate.getTitle()).startedAt(candidate.getStartedAt()).conversationDate(date)
                        .turnCount(0).estimatedTokens(0).isSuccess(false).build()));
        var existing = versions.findByConversationIdAndContentHash(conversation.getId(), candidate.getContentHash());
        if (existing.isPresent()) {
            var version = existing.get();
            if (version.getDeletedAt() != null
                    || !items.existsByVersionIdAndStatus(version.getId(), AnalysisItemStatus.FAILED)
                    || items.existsByVersionIdAndStatusNot(version.getId(), AnalysisItemStatus.FAILED)) {
                throw new CustomException(ErrorCode.DUPLICATE_CONTENT);
            }
            return version;
        }
        int inputVersion = versions.findFirstByConversationIdOrderByInputVersionDesc(conversation.getId())
                .map(ConversationVersion::getInputVersion).orElse(0) + 1;
        var version = versions.save(ConversationVersion.builder().conversation(conversation).sourceUpload(upload)
                .inputVersion(inputVersion).contentHash(candidate.getContentHash()).title(candidate.getTitle())
                .startedAt(candidate.getStartedAt().atZone(KST).toInstant()).conversationDate(date)
                .turnCount(turns.size()).excludedAttachments(source.excludedAttachments()).build());
        var rows = new ArrayList<Message>(turns.size());
        for (int index = 0; index < turns.size(); index++) {
            var turn = turns.get(index);
            rows.add(Message.builder().conversation(conversation).version(version).idx(index)
                    .role(turn.role()).content(turn.content()).createdAt(turn.createdAt())
                    .sourceModel(turn.model() == null ? null : turn.model().substring(0, Math.min(50, turn.model().length())))
                    .branchPoint(turn.branchPoint()).build());
        }
        messages.saveAll(rows);
        return version;
    }

    private static String validateKey(String key) {
        try {
            String normalized = UUID.fromString(key).toString();
            if (normalized.equalsIgnoreCase(key)) {
                return normalized;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Malformed and missing keys have the same response.
        }
        throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }

    private static LinkedHashMap<String, Long> validateSelections(AnalysisStartRequest request) {
        if (request.settingsVersion() == null || request.settingsVersion() < 0 || request.selections() == null
                || request.selections().isEmpty()
                || request.selections().size() > ServerPolicy.MAX_SELECTIONS_PER_REQUEST) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        var selections = new LinkedHashMap<String, Long>();
        for (var selection : request.selections()) {
            if (selection == null || selection.selectionId() == null || selection.selectionId().isBlank()
                    || selection.selectionId().length() > 40 || selection.taskTypeId() == null
                    || selection.taskTypeId() <= 0
                    || selections.putIfAbsent(selection.selectionId(), selection.taskTypeId()) != null) {
                throw new CustomException(ErrorCode.VALIDATION_FAILED);
            }
        }
        return selections;
    }

    static String hashRequest(Long uploadId, AnalysisStartRequest request) {
        var canonical = new StringBuilder().append(uploadId).append(':').append(request.settingsVersion());
        request.selections().stream().sorted(Comparator.comparing(AnalysisStartRequest.CandidateSelection::selectionId))
                .forEach(selection -> canonical.append(':').append(selection.selectionId().length())
                .append(':').append(selection.selectionId()).append(':').append(selection.taskTypeId()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record PreparedSource(Map<String, ChatGptConversationBranch.RestoredConversation> conversations, CustomException error) {
    }

    private static AnalysisStartResponse response(Analysis analysis) {
        String url = "/api/v1/analyses/" + analysis.getId();
        return new AnalysisStartResponse(analysis.getId(), analysis.getUpload().getId(), AnalysisStatus.QUEUED,
                analysis.getSelectedCount(), url, url + "/items", 3);
    }
}
