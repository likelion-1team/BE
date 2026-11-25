package com.babylion.team1.domain.dashboard.repository;

import com.babylion.team1.domain.dashboard.dto.CohortItem;
import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 대시보드 v4 지표의 집계 원본 조회 */
@Repository
@RequiredArgsConstructor
public class DashboardCohortRepository {

    private final EntityManager em;

    /**
     * 기준 버전 하나로 분석한 항목. 기간은 conversation_date(KST) 기준이며 양 끝을 포함한다.
     * 삭제된 대화 버전은 제외한다. userId가 null이면 팀 전체다.
     */
    public List<CohortItem> findItems(Long teamId, Long userId, Long settingsId, DatePeriod period) {
        String jpql = "select new " + CohortItem.class.getName() + """
                (i.id, v.id, i.user.id, i.conversationDate, s.version, i.status, i.current,
                 i.scopeClassification, i.confirmedOutcome, i.confirmedReworkType, i.estimatedTokens)
                from AnalysisItem i
                join i.version v
                join i.analysis a
                join a.settings s
                where i.team.id = :teamId
                  and s.id = :settingsId
                  and i.conversationDate between :from and :to
                  and v.deletedAt is null
                """ + (userId != null ? " and i.user.id = :userId" : "");
        TypedQuery<CohortItem> query = em.createQuery(jpql, CohortItem.class)
                .setParameter("teamId", teamId)
                .setParameter("settingsId", settingsId)
                .setParameter("from", period.from())
                .setParameter("to", period.to());
        if (userId != null) {
            query.setParameter("userId", userId);
        }
        return query.getResultList();
    }
}
