package com.babylion.team1.domain.dashboard.repository;

import com.babylion.team1.domain.dashboard.dto.ConversationAggregate;
import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.KpiCondition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 대시보드 집계 쿼리.
 * taskTypes·models 필터가 선택 값이라 조건에 따라 JPQL을 조립한다. 사용자 입력은 모두 파라미터로 바인딩한다.
 */
@Repository
@RequiredArgsConstructor
public class DashboardQueryRepository {

    private final EntityManager em;

    public ConversationAggregate aggregateConversations(KpiCondition condition, DatePeriod period) {
        StringBuilder jpql = new StringBuilder("""
                select count(c),
                       sum(case when c.isSuccess = true then 1 else 0 end),
                       sum(case when c.hasRework = true then 1 else 0 end),
                       sum(c.estimatedTokens),
                       sum(c.estimatedCost),
                       sum(c.reviewTimeSec),
                       sum(c.reworkTimeSec),
                       count(distinct c.user.id)
                from Conversation c
                where c.team.id = :teamId
                  and c.conversationDate between :from and :to
                """);
        if (condition.userId() != null) {
            jpql.append(" and c.user.id = :userId");
        }
        if (!condition.taskTypeIds().isEmpty()) {
            jpql.append(" and c.taskType.id in :taskTypeIds");
        }
        if (!condition.models().isEmpty()) {
            jpql.append(" and c.model in :models");
        }

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class);
        bindCommon(query, condition, period);
        if (!condition.models().isEmpty()) {
            query.setParameter("models", condition.models());
        }

        Object[] row = query.getSingleResult();
        long conversationCount = toLong(row[0]);
        if (conversationCount == 0) {
            return ConversationAggregate.empty();
        }
        return new ConversationAggregate(
                conversationCount,
                toLong(row[1]),
                toLong(row[2]),
                toLong(row[3]),
                toBigDecimal(row[4]),
                toLong(row[5]),
                toLong(row[6]),
                toLong(row[7]));
    }

    /**
     * 같은 기간(feedback_date 기준) 피드백의 예상 절감액 합.
     * feedbacks에는 모델 정보가 없어 models 필터는 적용하지 않는다.
     */
    public BigDecimal sumExpectedSavingCost(KpiCondition condition, DatePeriod period) {
        StringBuilder jpql = new StringBuilder("""
                select sum(f.expectedSavingCost)
                from Feedback f
                where f.team.id = :teamId
                  and f.feedbackDate between :from and :to
                """);
        if (condition.userId() != null) {
            jpql.append(" and f.user.id = :userId");
        }
        if (!condition.taskTypeIds().isEmpty()) {
            jpql.append(" and f.taskType.id in :taskTypeIds");
        }

        TypedQuery<BigDecimal> query = em.createQuery(jpql.toString(), BigDecimal.class);
        bindCommon(query, condition, period);
        BigDecimal sum = query.getSingleResult();
        return sum != null ? sum : BigDecimal.ZERO;
    }

    private void bindCommon(TypedQuery<?> query, KpiCondition condition, DatePeriod period) {
        query.setParameter("teamId", condition.teamId());
        query.setParameter("from", period.from());
        query.setParameter("to", period.to());
        if (condition.userId() != null) {
            query.setParameter("userId", condition.userId());
        }
        if (!condition.taskTypeIds().isEmpty()) {
            query.setParameter("taskTypeIds", condition.taskTypeIds());
        }
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /** 단가 없는 모델(estimated_cost NULL)은 sum에서 빠진다 */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
