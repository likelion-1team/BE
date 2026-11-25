package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.upload.dto.response.UploadResponse;
import com.babylion.team1.global.policy.ServerPolicy;
import java.time.ZoneId;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.upload.entity.UploadCandidate;
import com.babylion.team1.domain.upload.entity.UploadCandidate.DuplicateType;
import com.babylion.team1.domain.upload.repository.UploadCandidateRepository;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.domain.upload.service.ChatGptExportReader.ExportConversation;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UploadService {

    private static final int DUPLICATE_QUERY_BATCH_SIZE = 500;

    private final TeamMemberRepository teamMemberRepository;
    private final TeamSettingsRepository teamSettingsRepository;
    private final ConversationRepository conversationRepository;
    private final UploadRepository uploadRepository;
    private final UploadCandidateRepository candidateRepository;
    private final ChatGptExportReader reader;
    private final UploadFileStorage storage;

    @Transactional
    public UploadResponse upload(Long teamId, Long userId, MultipartFile file, String settingsVersion) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_A_MEMBER));
        TeamSettings settings = teamSettingsRepository.findByTeamId(teamId)
                .filter(TeamSettings::isTargetSet)
                .orElseThrow(() -> new CustomException(ErrorCode.VALIDATION_FAILED));
        int requestedVersion = parseVersion(settingsVersion);
        if (settings.getVersion() == 0) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        if (requestedVersion != settings.getVersion()) {
            throw new CustomException(ErrorCode.VERSION_CONFLICT);
        }
        String fileName = validateFile(file);
        Path staged = storage.stage(file);
        try {
            var export = reader.readCandidates(staged, fileName.toLowerCase(Locale.ROOT).endsWith(".zip"),
                    settings.getTargetFrom(), settings.getTargetTo());
            var conversations = export.candidates();
            Set<String> duplicates = findDuplicates(teamId, conversations);
            Upload upload = uploadRepository.saveAndFlush(Upload.builder()
                    .team(member.getTeam())
                    .uploader(member.getUser())
                    .settings(settings)
                    .fileName(fileName)
                    .conversationCount(conversations.size())
                    .duplicateCount(duplicates.size())
                    .targetFrom(settings.getTargetFrom())
                    .targetTo(settings.getTargetTo())
                    .build());
            Long uploadId = upload.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        storage.deleteUpload(uploadId);
                    }
                }
            });
            saveCandidates(upload, teamId, userId, conversations);
            storage.store(staged, uploadId);
            storage.writeTargetPeriod(uploadId,
                    new UploadFileStorage.TargetPeriod(settings.getTargetFrom(), settings.getTargetTo()));
            return new UploadResponse(uploadId, Upload.LifecycleStatus.READY, settings.getVersion(),
                    upload.getUploadedAt().plusMinutes(ServerPolicy.UPLOAD_RETENTION_MINUTES)
                            .atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                    export.conversationCount(), conversations.size(), "/api/v1/uploads/" + uploadId + "/conversations");
        } finally {
            storage.delete(staged);
        }
    }

    private void saveCandidates(Upload upload, Long teamId, Long userId,
                                List<ExportConversation> conversations) {
        for (int start = 0; start < conversations.size(); start += DUPLICATE_QUERY_BATCH_SIZE) {
            int end = Math.min(start + DUPLICATE_QUERY_BATCH_SIZE, conversations.size());
            var batch = conversations.subList(start, end);
            var ids = batch.stream().map(ExportConversation::externalId).toList();
            var hashes = new HashMap<String, String>();
            conversationRepository.findCurrentContentHashes(teamId, userId, ids)
                    .forEach(current -> hashes.put(current.getExternalId(), current.getContentHash()));

            candidateRepository.saveAll(batch.stream().map(conversation -> {
                String previousHash = hashes.get(conversation.externalId());
                DuplicateType duplicateType = DuplicateType.NONE;
                if (previousHash != null) {
                    duplicateType = previousHash.equals(conversation.contentHash())
                            ? DuplicateType.SAME_CONTENT : DuplicateType.CONTENT_UPDATED;
                }
                String title = conversation.title();
                if (title != null && title.codePointCount(0, title.length()) > 255) {
                    title = title.substring(0, title.offsetByCodePoints(0, 255));
                }
                return new UploadCandidate(upload, conversation.externalId(), title,
                        conversation.startedAt().toLocalDateTime(), conversation.contentHash(),
                        duplicateType, conversation.validationCode());
            }).toList());
        }
    }

    private int parseVersion(String value) {
        try {
            int version = Integer.parseInt(value);
            if (version >= 0) {
                return version;
            }
        } catch (NumberFormatException ignored) {
            // Missing and malformed versions use the same API error.
        }
        throw new CustomException(ErrorCode.VALIDATION_FAILED);
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new CustomException(ErrorCode.UPLOAD_TOO_LARGE);
        }
        String name = file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        String lowerCaseName = name.toLowerCase(Locale.ROOT);
        if (name.length() > 255 || name.codePoints().anyMatch(Character::isISOControl)
                || !(lowerCaseName.endsWith(".json") || lowerCaseName.endsWith(".zip"))) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        return name;
    }

    private Set<String> findDuplicates(Long teamId, List<ExportConversation> candidates) {
        List<String> ids = candidates.stream().map(ExportConversation::externalId).toList();
        Set<String> duplicates = new HashSet<>();
        for (int start = 0; start < ids.size(); start += DUPLICATE_QUERY_BATCH_SIZE) {
            int end = Math.min(start + DUPLICATE_QUERY_BATCH_SIZE, ids.size());
            duplicates.addAll(conversationRepository.findExistingExternalIds(teamId, ids.subList(start, end)));
        }
        return duplicates;
    }
}
