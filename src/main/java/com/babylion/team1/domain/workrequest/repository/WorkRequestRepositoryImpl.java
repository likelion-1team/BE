package com.babylion.team1.domain.workrequest.repository;

import com.babylion.team1.domain.workrequest.entity.RouteType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;

/** WorkRequestRepositoryCustom 구현. 빈 IN 절을 피하려고 필터가 있을 때만 조건을 붙인다. */
public class WorkRequestRepositoryImpl implements WorkRequestRepositoryCustom {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public List<RouteTypeCount> countByRouteType(RouteCountCondition condition) {
		StringBuilder jpql = new StringBuilder("""
				select new com.babylion.team1.domain.workrequest.repository.RouteTypeCount(w.routeType, count(w))
				from WorkRequest w
				where w.project.id in :projectIds
				  and w.createdAt >= :createdFrom
				  and w.createdAt < :createdTo
				  and w.routeType is not null
				""");
		if (!condition.taskTypes().isEmpty()) {
			jpql.append(" and w.taskType in :taskTypes");
		}
		if (!condition.policyVersionIds().isEmpty()) {
			jpql.append(" and w.policyVersion.id in :policyVersionIds");
		}
		if (!condition.modelVersions().isEmpty()) {
			jpql.append(" and w.routeType <> :ruleRoute")
					.append(" and exists (select 1 from AiExecutionLog l")
					.append(" where l.workRequest = w and l.modelVersion in :modelVersions)");
		}
		jpql.append(" group by w.routeType");

		TypedQuery<RouteTypeCount> query = entityManager.createQuery(jpql.toString(), RouteTypeCount.class)
				.setParameter("projectIds", condition.projectIds())
				.setParameter("createdFrom", condition.createdFrom())
				.setParameter("createdTo", condition.createdTo());
		if (!condition.taskTypes().isEmpty()) {
			query.setParameter("taskTypes", condition.taskTypes());
		}
		if (!condition.policyVersionIds().isEmpty()) {
			query.setParameter("policyVersionIds", condition.policyVersionIds());
		}
		if (!condition.modelVersions().isEmpty()) {
			query.setParameter("ruleRoute", RouteType.RULE);
			query.setParameter("modelVersions", condition.modelVersions());
		}
		return query.getResultList();
	}
}
