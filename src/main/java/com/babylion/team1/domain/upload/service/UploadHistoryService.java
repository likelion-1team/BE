package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.analysis.entity.Analysis;
import com.babylion.team1.domain.analysis.repository.AnalysisRepository;
import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.conversation.repository.ConversationRepository.UploadConversationCount;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.upload.dto.response.UploadHistoryResponse;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UploadHistoryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TeamMemberRepository teamMembers;
    private final UploadRepository uploads;
    private final AnalysisRepository analyses;
    private final ConversationRepository conversations;

    @Transactional(readOnly = true)
    public UploadHistoryResponse getMyUploads(Long teamId, Long userId, int page, int size) {
        teamMembers.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_A_MEMBER));

        if (page < 0 || size < 1 || (long) page * size > Integer.MAX_VALUE) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        var history = uploads.findByTeamIdAndUploaderIdOrderByUploadedAtDescIdDesc(
                teamId, userId, PageRequest.of(page, size));
        var uploadIds = history.stream().map(Upload::getId).toList();
        Map<Long, Analysis> analysisByUpload = uploadIds.isEmpty() ? Map.of() : analyses.findByUploadIdIn(uploadIds)
                .stream().collect(Collectors.toMap(analysis -> analysis.getUpload().getId(), analysis -> analysis));
        var completedIds = history.stream()
                .filter(upload -> upload.getStatus() == UploadStatus.DONE && !analysisByUpload.containsKey(upload.getId()))
                .map(Upload::getId)
                .toList();
        Map<Long, Long> counts = completedIds.isEmpty() ? Map.of() : conversations.countByUploadIds(completedIds)
                .stream().collect(Collectors.toMap(UploadConversationCount::getUploadId,
                        UploadConversationCount::getAnalyzedCount));
        var now = LocalDateTime.now();
        var items = history.stream().map(upload -> toItem(upload, analysisByUpload.get(upload.getId()),
                counts.getOrDefault(upload.getId(), 0L), now)).toList();
        return new UploadHistoryResponse(items, history.getNumber(), history.getSize(), history.getTotalElements());
    }

    private UploadHistoryResponse.Item toItem(Upload upload, Analysis analysis, long legacyCount, LocalDateTime now) {
        var period = upload.getTargetFrom() == null || upload.getTargetTo() == null ? null
                : new UploadHistoryResponse.Period(upload.getTargetFrom(), upload.getTargetTo());
        String uploadedAt = upload.getUploadedAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(KST)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        long analyzedCount = analysis == null ? legacyCount : analysis.getSucceededCount();
        return new UploadHistoryResponse.Item(upload.getId(), uploadedAt, analyzedCount, period, upload.lifecycleStatus(now),
                analysis == null ? null : analysis.getId(), analysis == null ? null : analysis.getStatus(),
                analysis == null ? null : analysis.getSucceededCount(), analysis == null ? null : analysis.getFailedCount());
    }
}
