package com.babylion.team1.domain.analysis.service;

import com.babylion.team1.domain.analysis.dto.OutcomeAssessmentRequest;
import com.babylion.team1.domain.analysis.dto.OutcomeAssessmentResponse;
import com.babylion.team1.domain.analysis.entity.AnalysisItemStatus;
import com.babylion.team1.domain.analysis.entity.AnalysisOutcome;
import com.babylion.team1.domain.analysis.entity.OutcomeAssessment;
import com.babylion.team1.domain.analysis.entity.ScopeClassification;
import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.analysis.repository.OutcomeAssessmentRepository;
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
public class OutcomeAssessmentService {
    private final AnalysisItemRepository items;
    private final OutcomeAssessmentRepository assessments;

    @Transactional
    public OutcomeAssessmentResponse save(Long itemId, Long userId, OutcomeAssessmentRequest request) {
        var item = items.findByIdAndUserIdAndVersionDeletedAtIsNull(itemId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (item.getStatus() != AnalysisItemStatus.SUCCEEDED || item.getScopeClassification() != ScopeClassification.IN_SCOPE) {
            throw new CustomException(ErrorCode.INVALID_STATE);
        }

        AnalysisOutcome outcome = validate(request);
        int current = assessments.findFirstByItemIdOrderByVersionDesc(itemId)
                .map(OutcomeAssessment::getVersion).orElse(0);
        if (request.expectedVersion() != current) {
            throw new CustomException(ErrorCode.VERSION_CONFLICT);
        }
        var assessment = new OutcomeAssessment(item, current + 1, outcome, request.note(),
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        try {
            assessments.saveAndFlush(assessment);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().endsWith("uk_outcome_assessments_item_version")) {
                throw new CustomException(ErrorCode.VERSION_CONFLICT);
            }
            throw exception;
        }
        item.confirmOutcome(outcome);
        return OutcomeAssessmentResponse.from(assessment);
    }

    private static AnalysisOutcome validate(OutcomeAssessmentRequest request) {
        if (request.expectedVersion() == null || request.expectedVersion() < 0 || request.outcome() == null
                || (request.note() != null && request.note().codePointCount(0, request.note().length()) > 500)) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        try {
            return AnalysisOutcome.valueOf(request.outcome());
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
