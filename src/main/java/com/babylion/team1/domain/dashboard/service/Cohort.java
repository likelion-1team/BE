package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.CohortItem;
import com.babylion.team1.domain.dashboard.dto.response.KpiResponse.Coverage;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 조회 조건에 걸린 분석 항목 묶음. KPI 카드와 추이가 함께 쓴다. */
record Cohort(List<CohortItem> items) {

    /** 지표 집계 대상 */
    List<CohortItem> inScopeItems() {
        return items.stream().filter(CohortItem::isInScope).toList();
    }

    /** 같은 대화 버전을 다시 분석했으면 최신 항목만 센다. 새 버전으로 교체된 성공 항목은 어디에도 세지 않는다. */
    Coverage coverage() {
        Collection<CohortItem> latest = items.stream()
                .collect(Collectors.toMap(CohortItem::versionId, Function.identity(),
                        BinaryOperator.maxBy(Comparator.comparing(CohortItem::itemId))))
                .values();
        List<CohortItem> inScope = latest.stream().filter(CohortItem::isInScope).toList();
        long excluded = count(latest, CohortItem::isExcluded);
        long pending = count(latest, CohortItem::isPending);
        long failed = count(latest, CohortItem::isFailed);
        return new Coverage(
                inScope.size() + excluded + pending + failed,
                inScope.size(),
                excluded,
                pending,
                failed,
                count(inScope, CohortItem::isOutcomeConfirmed),
                count(inScope, CohortItem::isReworkConfirmed),
                count(inScope, CohortItem::isTokenMeasured));
    }

    private static long count(Collection<CohortItem> items, Predicate<CohortItem> predicate) {
        return items.stream().filter(predicate).count();
    }
}
