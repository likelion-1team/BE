package com.babylion.team1.domain.dashboard.dto;

import com.babylion.team1.domain.analysis.entity.AnalysisItemStatus;
import com.babylion.team1.domain.analysis.entity.AnalysisOutcome;
import com.babylion.team1.domain.analysis.entity.AnalysisReworkType;
import com.babylion.team1.domain.analysis.entity.ScopeClassification;
import java.time.LocalDate;

/**
 * 조회 조건(팀·범위·기간·기준 버전)에 걸린 분석 항목 하나. 삭제된 대화 버전의 항목은 담기지 않는다.
 * outcome·reworkType은 직원이 확인한 값이며, 확인하지 않았으면 null이다(AI 추정 값은 쓰지 않는다).
 */
public record CohortItem(
        Long itemId,
        Long versionId,
        Long userId,
        LocalDate conversationDate,
        int settingsVersion,
        AnalysisItemStatus status,
        boolean current,
        ScopeClassification scopeClassification,
        AnalysisOutcome outcome,
        AnalysisReworkType reworkType,
        Integer estimatedTokens
) {

    /** 지표 집계 대상: 분석에 성공한 현행 항목 중 IN_SCOPE */
    public boolean isInScope() {
        return isCurrentSuccess() && scopeClassification == ScopeClassification.IN_SCOPE;
    }

    /** 분석에 성공했지만 업무 범위 밖으로 판정된 현행 항목 */
    public boolean isExcluded() {
        return isCurrentSuccess() && scopeClassification != ScopeClassification.IN_SCOPE;
    }

    public boolean isPending() {
        return status == AnalysisItemStatus.QUEUED || status == AnalysisItemStatus.RUNNING;
    }

    public boolean isFailed() {
        return status == AnalysisItemStatus.FAILED;
    }

    public boolean isSuccessConfirmed() {
        return outcome == AnalysisOutcome.SUCCESS;
    }

    /** UNKNOWN은 확인한 것으로 보지 않는다 */
    public boolean isOutcomeConfirmed() {
        return outcome == AnalysisOutcome.SUCCESS || outcome == AnalysisOutcome.FAILURE;
    }

    /** UNKNOWN은 확인한 것으로 보지 않는다 */
    public boolean isReworkConfirmed() {
        return reworkType != null && reworkType != AnalysisReworkType.UNKNOWN;
    }

    public boolean isErrorRework() {
        return reworkType == AnalysisReworkType.ERROR_CORRECTION;
    }

    public boolean isTokenMeasured() {
        return estimatedTokens != null;
    }

    private boolean isCurrentSuccess() {
        return status == AnalysisItemStatus.SUCCEEDED && current;
    }
}
