package com.babylion.team1.domain.analysis.service;

import com.babylion.team1.domain.analysis.dto.ReworkAssessmentRequest;
import com.babylion.team1.domain.analysis.dto.ReworkAssessmentResponse;
import com.babylion.team1.domain.analysis.entity.AnalysisItemStatus;
import com.babylion.team1.domain.analysis.entity.AnalysisReworkType;
import com.babylion.team1.domain.analysis.entity.ReworkAssessment;
import com.babylion.team1.domain.analysis.entity.ScopeClassification;
import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.analysis.repository.ReworkAssessmentRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReworkAssessmentService {
    private final AnalysisItemRepository items;
    private final ReworkAssessmentRepository assessments;

    @Transactional
    public ReworkAssessmentResponse save(Long itemId, Long userId, ReworkAssessmentRequest request) {
        var item = items.findByIdAndUserIdAndVersionDeletedAtIsNull(itemId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (item.getStatus() != AnalysisItemStatus.SUCCEEDED || item.getScopeClassification() != ScopeClassification.IN_SCOPE) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        AnalysisReworkType type = validate(request);
        int current = assessments.findFirstByItemIdOrderByVersionDesc(itemId)
                .map(ReworkAssessment::getVersion).orElse(0);
        if (request.expectedVersion() != current) {
            throw new CustomException(ErrorCode.VERSION_CONFLICT);
        }
        var assessment = new ReworkAssessment(item, current + 1, type, request.note(),
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        try {
            assessments.saveAndFlush(assessment);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().endsWith("uk_rework_assessments_item_version")) {
                throw new CustomException(ErrorCode.VERSION_CONFLICT);
            }
            throw exception;
        }
        item.confirmReworkType(type);
        return ReworkAssessmentResponse.from(assessment);
    }

    private static AnalysisReworkType validate(ReworkAssessmentRequest request) {
        if (request.expectedVersion() == null || request.expectedVersion() < 0 || request.type() == null
                || (request.note() != null && request.note().codePointCount(0, request.note().length()) > 500)) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        try {
            return AnalysisReworkType.valueOf(request.type());
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
