
package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.request.FlowChartRequest;
import com.babylion.team1.domain.dashboard.dto.response.FlowChartResponse;
import com.babylion.team1.domain.dashboard.dto.response.FlowChartResponse.Flow;
import com.babylion.team1.domain.dashboard.dto.response.FlowChartResponse.Node;
import com.babylion.team1.domain.project.entity.Project;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserProjectRepository;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.domain.workrequest.entity.RouteType;
import com.babylion.team1.domain.workrequest.repository.RouteCountCondition;
import com.babylion.team1.domain.workrequest.repository.RouteTypeCount;
import com.babylion.team1.domain.workrequest.repository.WorkRequestRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경로 흐름 차트 집계.
 * 1단계: 접수 → 규칙 확정(RULE) / 판단형 AI(그 외 전부)
 * 2단계: 판단형 AI → ERP 직결 / LLM / 추가 질문 / 담당자 검토
 * ratio는 부모 노드 건수 대비 %(소수 첫째 자리 반올림)이며, 부모 건수가 0이면 0.0이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardFlowChartService {

	private static final List<FlowNode> AI_TARGETS =
			List.of(FlowNode.ERP_DIRECT, FlowNode.LLM, FlowNode.ADD_QUESTION, FlowNode.REVIEW);

	private final UserRepository userRepository;
	private final UserProjectRepository userProjectRepository;
	private final WorkRequestRepository workRequestRepository;

	public FlowChartResponse getFlowChart(Long userId, FlowChartRequest request) {
		if (request.endDate().isBefore(request.startDate())) {
			throw new CustomException(ErrorCode.INVALID_DATE_RANGE);
		}

		List<Long> projectIds = resolveProjectIds(userId, request.projects());

		RouteCountCondition condition = new RouteCountCondition(
				projectIds,
				request.startDate().atStartOfDay(),
				request.endDate().plusDays(1).atStartOfDay(),
				request.taskTypes(),
				request.policyVersions(),
				request.modelVersions());
		Map<RouteType, Long> countByRoute = toCountMap(workRequestRepository.countByRouteType(condition));

		return buildResponse(countByRoute);
	}

	/** 요청한 프로젝트가 없으면 접근 가능한 전체 프로젝트, 있으면 모두 접근 가능한지 확인 후 그대로 사용한다. */
	private List<Long> resolveProjectIds(Long userId, List<Long> requestedProjectIds) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		Set<Long> accessibleIds = userProjectRepository
				.findAccessibleProjects(userId, user.getCompany().getId()).stream()
				.map(Project::getId)
				.collect(Collectors.toSet());

		if (requestedProjectIds.isEmpty()) {
			if (accessibleIds.isEmpty()) {
				throw new CustomException(ErrorCode.NO_ACCESSIBLE_PROJECT);
			}
			return List.copyOf(accessibleIds);
		}
		if (!accessibleIds.containsAll(requestedProjectIds)) {
			throw new CustomException(ErrorCode.PROJECT_ACCESS_DENIED);
		}
		return requestedProjectIds;
	}

	private Map<RouteType, Long> toCountMap(List<RouteTypeCount> rows) {
		Map<RouteType, Long> counts = new EnumMap<>(RouteType.class);
		for (RouteTypeCount row : rows) {
			counts.merge(row.routeType(), row.count(), Long::sum);
		}
		return counts;
	}

	private FlowChartResponse buildResponse(Map<RouteType, Long> countByRoute) {
		long ruleCount = count(countByRoute, FlowNode.RULE_CONFIRM);
		long aiCount = AI_TARGETS.stream().mapToLong(node -> count(countByRoute, node)).sum();
		long totalCount = ruleCount + aiCount;

		List<Flow> flows = new ArrayList<>();
		flows.add(flow(FlowNode.RECEIPT, FlowNode.RULE_CONFIRM, ruleCount, totalCount));
		flows.add(flow(FlowNode.RECEIPT, FlowNode.DECISION_AI, aiCount, totalCount));
		for (FlowNode target : AI_TARGETS) {
			flows.add(flow(FlowNode.DECISION_AI, target, count(countByRoute, target), aiCount));
		}
		return new FlowChartResponse(totalCount, flows);
	}

	private long count(Map<RouteType, Long> countByRoute, FlowNode node) {
		return countByRoute.getOrDefault(node.getRouteType(), 0L);
	}

	private Flow flow(FlowNode source, FlowNode target, long trafficCount, long parentCount) {
		return new Flow(toNode(source), toNode(target), trafficCount, ratio(trafficCount, parentCount));
	}

	private Node toNode(FlowNode node) {
		return new Node(node.name(), node.getLabel());
	}

	private double ratio(long count, long parentCount) {
		if (parentCount == 0) {
			return 0.0;
		}
		return BigDecimal.valueOf(count)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(parentCount), 1, RoundingMode.HALF_UP)
				.doubleValue();
	}
}
