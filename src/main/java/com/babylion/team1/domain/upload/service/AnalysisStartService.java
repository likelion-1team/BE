package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.team.repository.TeamTaskTypeRepository;
import com.babylion.team1.domain.upload.dto.request.AnalysisStartRequest.Selection;
import com.babylion.team1.domain.upload.dto.response.AnalysisStartResponse;
import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisStartService {

    private final UploadRepository uploads;
    private final TeamTaskTypeRepository taskTypes;
    private final TeamSettingsRepository settings;
    private final UploadFileStorage storage;
    private final ApplicationEventPublisher events;
    private final ChatGptExportReader reader;

    @Transactional
    public AnalysisStartResponse start(Long uploadId, Long userId, List<Selection> selections) {
        var upload = uploads.findByIdForUpdate(uploadId)
                .orElseThrow(() -> new CustomException(ErrorCode.UPLOAD_NOT_FOUND));
        if (!upload.isUploadedBy(userId)) {
            throw new CustomException(ErrorCode.NOT_UPLOADER);
        }
        switch (upload.getStatus()) {
            case ANALYZING -> throw new CustomException(ErrorCode.ANALYSIS_IN_PROGRESS);
            case DONE -> throw new CustomException(ErrorCode.ANALYSIS_ALREADY_COMPLETED);
            case UPLOADED, FAILED -> {
                if (!Files.isRegularFile(storage.pathFor(uploadId))
                        || UploadFileExpiry.isExpired(upload, storage.pathFor(uploadId), LocalDateTime.now())) {
                    throw new CustomException(ErrorCode.UPLOAD_EXPIRED);
                }
            }
        }
        if (selections == null || selections.isEmpty()) {
            throw new CustomException(ErrorCode.NO_CONVERSATION_SELECTED);
        }

        Long teamId = upload.getTeam().getId();
        Set<Long> allowedTaskTypes = taskTypes.findByTeamIdAndIsActiveTrueOrderById(teamId).stream()
                .map(taskType -> taskType.getId())
                .collect(Collectors.toSet());
        Set<String> selectedIds = new HashSet<>();
        for (Selection selection : selections) {
            if (!allowedTaskTypes.contains(selection.taskTypeId())) {
                throw new CustomException(ErrorCode.TASK_TYPE_NOT_ALLOWED);
            }
            if (!selectedIds.add(selection.externalId())) {
                throw new CustomException(ErrorCode.INVALID_REQUEST, "같은 대화를 여러 번 선택할 수 없습니다.");
            }
        }

        var period = upload.getTargetFrom() != null && upload.getTargetTo() != null
                ? new UploadFileStorage.TargetPeriod(upload.getTargetFrom(), upload.getTargetTo())
                : storage.readTargetPeriod(uploadId).orElseGet(() -> settings.findByTeamId(teamId)
                .filter(teamSettings -> teamSettings.isTargetSet())
                .map(teamSettings -> new UploadFileStorage.TargetPeriod(
                        teamSettings.getTargetFrom(), teamSettings.getTargetTo()))
                .orElseThrow(() -> new CustomException(ErrorCode.TARGET_NOT_SET)));
        boolean zipped = upload.getFileName().toLowerCase(Locale.ROOT).endsWith(".zip");
        var candidates = reader.read(storage.pathFor(uploadId), zipped, period.from(), period.to()).stream()
                .map(ChatGptExportReader.ExportConversation::externalId).collect(Collectors.toSet());
        if (!candidates.containsAll(selectedIds)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "분석 대상 기간의 업로드 후보만 선택할 수 있습니다.");
        }
        upload.startAnalysis(selections.size());
        events.publishEvent(new AnalysisJob(uploadId, upload.getFileName(), period, List.copyOf(selections)));
        return new AnalysisStartResponse(uploadId, UploadStatus.ANALYZING);
    }
}
