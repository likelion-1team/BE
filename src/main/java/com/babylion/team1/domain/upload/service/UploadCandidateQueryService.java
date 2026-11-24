package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.upload.dto.response.UploadCandidatesResponse;
import com.babylion.team1.domain.upload.dto.response.UploadCandidatesResponse.Item;
import com.babylion.team1.domain.upload.entity.UploadCandidate;
import com.babylion.team1.domain.upload.entity.UploadCandidate.DuplicateType;
import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.upload.repository.UploadCandidateRepository;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UploadCandidateQueryService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UploadRepository uploads;
    private final UploadCandidateRepository candidates;

    public UploadCandidatesResponse getCandidates(Long uploadId, Long userId, String query, int page, int size) {
        var upload = uploads.findById(uploadId).filter(row -> row.isUploadedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!upload.getUploadedAt().plusHours(1).isAfter(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.UPLOAD_EXPIRED);
        }
        if (page < 1 || size < 1 || size > 100) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if (upload.getStatus() == UploadStatus.CONSUMED) {
            return new UploadCandidatesResponse(List.of(), page, size, 0);
        }
        var pageable = PageRequest.of(page - 1, size);
        var rows = query == null || query.isBlank()
                ? candidates.findByUploadIdOrderByStartedAtDescIdAsc(uploadId, pageable)
                : candidates.findByUploadIdAndTitleContainingIgnoreCaseOrderByStartedAtDescIdAsc(
                        uploadId, query.strip(), pageable);
        if (rows.isEmpty()) {
            return new UploadCandidatesResponse(List.of(), page, size, rows.getTotalElements());
        }
        var reservedIds = candidates.findReservedCandidateIds(rows.stream().map(UploadCandidate::getId).toList());
        var items = rows.stream().map(row -> toItem(row, reservedIds.contains(row.getId()))).toList();
        return new UploadCandidatesResponse(items, page, size, rows.getTotalElements());
    }

    private static Item toItem(UploadCandidate row, boolean reserved) {
        var duplicateType = reserved ? DuplicateType.SAME_CONTENT : row.getDuplicateType();
        String validationCode = row.getValidationCode();
        if (validationCode == null && duplicateType == DuplicateType.SAME_CONTENT) {
            validationCode = "ALREADY_ANALYZED";
        }
        return new Item(row.getSelectionId(), row.getTitle(), row.getStartedAt().atZone(KST).toOffsetDateTime(),
                row.getExternalId(), duplicateType, row.isSelectable() && !reserved, validationCode);
    }
}
