package com.babylion.team1.domain.dashboard.repository;

import com.babylion.team1.domain.dashboard.dto.ConversationAggregate;
import com.babylion.team1.domain.dashboard.dto.CostRow;
import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.KpiCondition;
import com.babylion.team1.domain.dashboard.dto.request.CostGroupBy;
import com.babylion.team1.domain.dashboard.dto.response.DayConversationItem;
import com.babylion.team1.domain.dashboard.dto.response.TaskTypeOption;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 대시보드 집계 쿼리.
 * taskTypes·models 필터가 선택 값이라 조건에 따라 JPQL을 조립한다. 사용자 입력은 모두 파라미터로 바인딩한다.
 * 결과에 NULL이 없는 조회는 생성자 표현식(select new)으로 바로 DTO를 만들고,
 * sum 결과가 NULL일 수 있는 집계는 Object[]로 받아 0으로 바꾼다.
 */
@Repository
@RequiredArgsConstructor
public class DashboardQueryRepository {

    private final EntityManager em;

    /**
     * 분석이 끝난 대화만 남기는 조인과 조건. 분석 전·실패·삭제된 대화를 제외한다.
     * v4 대화는 분석에 성공해야 현행 버전이 생기고, v3 대화는 버전 없이 업로드가 DONE이다.
     */
    private static final String ANALYZED_JOIN = " join c.upload u left join c.currentVersion cv ";
    private static final String ANALYZED_CONDITION =
            " and ((cv.id is null and u.status = DONE) or (cv.id is not null and cv.deletedAt is null)) ";

    /** ConversationAggregate 필드 순서와 같다. 0~3번 컬럼 */
    private static final String AGGREGATE_COLUMNS = """
            count(c),
            sum(case when c.isSuccess = true then 1 else 0 end),
            sum(case when c.hasRework = true then 1 else 0 end),
            sum(c.estimatedTokens)
            """;
    /** AGGREGATE_COLUMNS 뒤에 붙인 컬럼(그룹 키)이 시작하는 위치 */
    private static final int AFTER_AGGREGATE = 4;

    /** 기간 전체를 하나로 집계한다 */
    public ConversationAggregate aggregateConversations(KpiCondition condition, DatePeriod period) {
        String jpql = "select " + AGGREGATE_COLUMNS + conversationWhere(condition);
        TypedQuery<Object[]> query = em.createQuery(jpql, Object[].class);
        bindConversation(query, condition, period);
        return toAggregate(query.getSingleResult());
    }

    /**
     * 날짜 x 그룹 키별로 호출비 합과 검토·수습 시간 합을 구한다. 대화가 있는 조합만 담긴다.
     * 키: MODEL이면 모델명(NULL이면 null 그대로, 서비스에서 UNKNOWN 처리), TASK_TYPE이면 업무 유형명, COST_TYPE이면 null.
     * 호출비는 estimated_cost 합이며, 단가 없는 모델(NULL)은 합에서 빠져 0으로 본다.
     */
    public List<CostRow> aggregateCostsByDate(KpiCondition condition, DatePeriod period, CostGroupBy groupBy) {
        String keyExpression = switch (groupBy) {
            case MODEL -> "c.model";
            case TASK_TYPE -> "c.taskType.name";
            case COST_TYPE -> null;
        };
        // 키가 있으면 [날짜, 키, 호출비, 검토초, 수습초], 없으면(COST_TYPE) [날짜, 호출비, 검토초, 수습초]
        boolean hasKey = keyExpression != null;
        String select = "select c.conversationDate" + (hasKey ? ", " + keyExpression : "")
                + ", sum(c.estimatedCost), sum(c.reviewTimeSec), sum(c.reworkTimeSec)";
        String groupByClause = " group by c.conversationDate" + (hasKey ? ", " + keyExpression : "");
        TypedQuery<Object[]> query = em.createQuery(select + conversationWhere(condition) + groupByClause,
                Object[].class);
        bindConversation(query, condition, period);

        int offset = hasKey ? 1 : 0;
        return query.getResultList().stream()
                .map(row -> new CostRow(
                        (LocalDate) row[0],
                        hasKey ? (String) row[1] : null,
                        toBigDecimal(row[1 + offset]),
                        toLong(row[2 + offset]),
                        toLong(row[3 + offset])))
                .toList();
    }

    /**
     * 업무 유형별로 집계한다. 기간 안에 대화가 있는 업무 유형만 담기며, 업무 유형 id 오름차순이다.
     * 결과에 사용자 정보는 들어가지 않는다.
     */
    public Map<TaskTypeOption, ConversationAggregate> aggregateConversationsByTaskType(KpiCondition condition,
            DatePeriod period) {
        String jpql = "select " + AGGREGATE_COLUMNS + ", c.taskType.id, c.taskType.name " + conversationWhere(condition)
                + " group by c.taskType.id, c.taskType.name order by c.taskType.id";
        TypedQuery<Object[]> query = em.createQuery(jpql, Object[].class);
        bindConversation(query, condition, period);

        Map<TaskTypeOption, ConversationAggregate> result = new LinkedHashMap<>();
        for (Object[] row : query.getResultList()) {
            TaskTypeOption taskType = new TaskTypeOption((Long) row[AFTER_AGGREGATE], (String) row[AFTER_AGGREGATE + 1]);
            result.put(taskType, toAggregate(row));
        }
        return result;
    }

    /**
     * 업무 유형별로 가장 많이 나온 피드백 유형을 최대 limit개씩 돌려준다(key: 업무 유형 id).
     * 같은 기간(feedback_date 기준) 피드백 건수가 많은 순, 건수가 같으면 유형 이름순이다.
     * feedbacks에는 모델 정보가 없어 models 필터는 적용하지 않는다.
     */
    public Map<Long, List<String>> findTopFeedbackTypes(KpiCondition condition, DatePeriod period, int limit) {
        String jpql = "select f.taskType.id, f.type, count(f)" + feedbackWhere(condition)
                + " group by f.taskType.id, f.type order by f.taskType.id, count(f) desc, f.type";
        TypedQuery<Object[]> query = em.createQuery(jpql, Object[].class);
        bindCommon(query, condition, period);

        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (Object[] row : query.getResultList()) {
            List<String> types = result.computeIfAbsent((Long) row[0], id -> new ArrayList<>());
            if (types.size() < limit) {
                types.add((String) row[1]);
            }
        }
        return result;
    }

    /** 그날(conversation_date) 본인 대화 목록. startedAt 오름차순, 같으면 id 순 */
    public List<DayConversationItem> findDayConversations(Long teamId, Long userId, LocalDate date) {
        String jpql = "select new " + DayConversationItem.class.getName() + """
                (c.id, c.title, c.startedAt, c.model, t.name,
                       c.turnCount, c.estimatedTokens, c.isSuccess, c.hasRework)
                from Conversation c join c.taskType t
                """ + ANALYZED_JOIN + """
                where c.team.id = :teamId
                  and c.user.id = :userId
                  and c.conversationDate = :date
                """ + ANALYZED_CONDITION + " order by c.startedAt, c.id";
        return em.createQuery(jpql, DayConversationItem.class)
                .setParameter("teamId", teamId)
                .setParameter("userId", userId)
                .setParameter("date", date)
                .getResultList();
    }

    private String conversationWhere(KpiCondition condition) {
        StringBuilder jpql = new StringBuilder("""
                 from Conversation c
                """ + ANALYZED_JOIN + """
                where c.team.id = :teamId
                  and c.conversationDate between :from and :to
                """ + ANALYZED_CONDITION);
        if (condition.userId() != null) {
            jpql.append(" and c.user.id = :userId");
        }
        if (!condition.taskTypeIds().isEmpty()) {
            jpql.append(" and c.taskType.id in :taskTypeIds");
        }
        if (!condition.models().isEmpty()) {
            jpql.append(" and c.model in :models");
        }
        return jpql.toString();
    }

    /** 근거 원문이 삭제된 피드백은 제외한다. feedbacks에는 모델 정보가 없어 models 조건은 넣지 않는다. 바인딩은 bindCommon */
    private String feedbackWhere(KpiCondition condition) {
        StringBuilder jpql = new StringBuilder("""
                 from Feedback f
                where f.team.id = :teamId
                  and f.feedbackDate between :from and :to
                  and not exists (
                      select 1 from FeedbackEvidence e left join e.conversation.currentVersion currentVersion
                      where e.feedback = f and
                """ + FeedbackEvidenceRepository.DELETED_ORIGINAL + ")");
        if (condition.userId() != null) {
            jpql.append(" and f.user.id = :userId");
        }
        if (!condition.taskTypeIds().isEmpty()) {
            jpql.append(" and f.taskType.id in :taskTypeIds");
        }
        return jpql.toString();
    }

    private void bindConversation(TypedQuery<?> query, KpiCondition condition, DatePeriod period) {
        bindCommon(query, condition, period);
        if (!condition.models().isEmpty()) {
            query.setParameter("models", condition.models());
        }
    }

    private ConversationAggregate toAggregate(Object[] row) {
        long conversationCount = toLong(row[0]);
        if (conversationCount == 0) {
            return ConversationAggregate.empty();
        }
        return new ConversationAggregate(
                conversationCount,
                toLong(row[1]),
                toLong(row[2]),
                toLong(row[3]));
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
