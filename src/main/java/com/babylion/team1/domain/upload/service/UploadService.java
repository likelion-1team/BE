package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.upload.dto.response.UploadResponse;
import com.babylion.team1.domain.upload.dto.response.UploadResponse.Candidate;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.domain.upload.service.ChatGptExportReader.ExportConversation;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.nio.file.Path;
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
    private final ChatGptExportReader reader;
    private final UploadFileStorage storage;

    @Transactional
    public UploadResponse upload(Long teamId, Long userId, MultipartFile file) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_A_MEMBER));
        TeamSettings settings = teamSettingsRepository.findByTeamId(teamId)
                .filter(TeamSettings::isTargetSet)
                .orElseThrow(() -> new CustomException(ErrorCode.TARGET_NOT_SET));
        String fileName = validateFile(file);
        Path staged = storage.stage(file);
        try {
            List<ExportConversation> conversations = reader.read(staged, fileName.toLowerCase(Locale.ROOT).endsWith(".zip"),
                    settings.getTargetFrom(), settings.getTargetTo());
            Set<String> duplicates = findDuplicates(teamId, conversations);
            List<Candidate> candidates = conversations.stream()
                    .map(conversation -> new Candidate(conversation.externalId(), conversation.title(),
                            conversation.startedAt(), duplicates.contains(conversation.externalId())))
                    .toList();
            Upload upload = uploadRepository.saveAndFlush(Upload.builder()
                    .team(member.getTeam())
                    .uploader(member.getUser())
                    .fileName(fileName)
                    .conversationCount(candidates.size())
                    .duplicateCount(duplicates.size())
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
            storage.store(staged, uploadId);
            storage.writeTargetPeriod(uploadId,
                    new UploadFileStorage.TargetPeriod(settings.getTargetFrom(), settings.getTargetTo()));
            return new UploadResponse(uploadId, candidates);
        } finally {
            storage.delete(staged);
        }
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw new CustomException(ErrorCode.INVALID_FILE_FORMAT);
        }
        String name = file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        String lowerCaseName = name.toLowerCase(Locale.ROOT);
        if (name.length() > 255 || name.codePoints().anyMatch(Character::isISOControl)
                || !(lowerCaseName.endsWith(".json") || lowerCaseName.endsWith(".zip"))) {
            throw new CustomException(ErrorCode.INVALID_FILE_FORMAT);
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
