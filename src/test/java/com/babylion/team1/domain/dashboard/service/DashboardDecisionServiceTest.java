package com.babylion.team1.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.babylion.team1.domain.audit.entity.AuditEventType;
import com.babylion.team1.domain.audit.entity.AuditLog;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.dashboard.dto.request.DecisionRecordRequest;
import com.babylion.team1.domain.dashboard.dto.response.DecisionRecordResponse;
import com.babylion.team1.domain.policy.entity.DecisionResult;
import com.babylion.team1.domain.policy.entity.Policy;
import com.babylion.team1.domain.policy.entity.PolicyRecommendation;
import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import com.babylion.team1.domain.policy.repository.PolicyRecommendationRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** DashboardDecisionService 단위 테스트 (Repository Mock, DB 불필요) */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardDecisionServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long COMPANY_ID = 10L;
	private static final Long POLICY_ID = 20L;
	private static final Long RECOMMENDATION_ID = 30L;
	private static final Long AUDIT_ID = 5042L;

	@Mock
	private UserRepository userRepository;
	@Mock
	private PolicyRecommendationRepository policyRecommendationRepository;
	@Mock
	private AuditLogRepository auditLogRepository;

	private final ObjectMapper objectMapper = JsonMapper.builder().build();
	private DashboardDecisionService service;

	private Company company;
	private User approver;

	@BeforeEach
	void setUp() {
		service = new DashboardDecisionService(
				userRepository, policyRecommendationRepository, auditLogRepository, objectMapper);

		company = company(COMPANY_ID);
		approver = user(company, RoleType.APPROVER);
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(approver));
		given(userRepository.getReferenceById(USER_ID)).willReturn(approver);
		given(policyRecommendationRepository.findWithPolicyById(RECOMMENDATION_ID))
				.willReturn(Optional.of(recommendation(company, "EXPAND")));
		given(policyRecommendationRepository.recordDecision(anyLong(), any(), any(), any(), any(), any()))
				.willReturn(1);
		given(auditLogRepository.save(any(AuditLog.class))).willAnswer(invocation -> {
			AuditLog log = invocation.getArgument(0);
			ReflectionTestUtils.setField(log, "id", AUDIT_ID);
			return log;
		});
	}

	@Test
	@DisplayName("시스템 제안과 같은 결정이면 APPROVED로 갱신하고 감사 로그를 남긴다")
	void recordDecisionFollowingProposal() {
		DecisionRecordResponse response = service.recordDecision(USER_ID,
				new DecisionRecordRequest(RECOMMENDATION_ID, DecisionResult.EXPAND, "전사 확대"));

		assertThat(response.auditId()).isEqualTo(AUDIT_ID);
		assertThat(response.recommendationId()).isEqualTo(RECOMMENDATION_ID);
		assertThat(response.decision()).isEqualTo(DecisionResult.EXPAND);
		assertThat(response.decidedAt().getOffset().getId()).isEqualTo("+09:00");

		verify(policyRecommendationRepository).recordDecision(eq(RECOMMENDATION_ID),
				eq(RecommendationStatus.APPROVED), eq(DecisionResult.EXPAND), isNull(), eq(approver),
				eq(response.decidedAt().toLocalDateTime()));

		ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		AuditLog saved = captor.getValue();
		assertThat(saved.getEventType()).isEqualTo(AuditEventType.DECISION_RECORDED);
		assertThat(saved.getUser()).isSameAs(approver);

		JsonNode detail = objectMapper.readTree(saved.getDetail());
		assertThat(detail.get("recommendationId").asLong()).isEqualTo(RECOMMENDATION_ID);
		assertThat(detail.get("policyId").asLong()).isEqualTo(POLICY_ID);
		assertThat(detail.get("systemProposal").asString()).isEqualTo("EXPAND");
		assertThat(detail.get("decision").asString()).isEqualTo("EXPAND");
		assertThat(detail.get("reason").asString()).isEqualTo("전사 확대");
	}

	@Test
	@DisplayName("시스템 제안과 같은 결정이면 reason 없이도 기록된다")
	void recordDecisionWithoutReasonWhenFollowingProposal() {
		service.recordDecision(USER_ID, new DecisionRecordRequest(RECOMMENDATION_ID, DecisionResult.EXPAND, null));

		verify(policyRecommendationRepository).recordDecision(eq(RECOMMENDATION_ID),
				eq(RecommendationStatus.APPROVED), eq(DecisionResult.EXPAND), isNull(), eq(approver), any());
	}

	@Test
	@DisplayName("시스템 제안과 다른 결정이면 REJECTED로 갱신하고 reason을 rejection_reason에 저장한다")
	void recordDecisionAgainstProposal() {
		service.recordDecision(USER_ID,
				new DecisionRecordRequest(RECOMMENDATION_ID, DecisionResult.HOLD, "  추가 검증 필요  "));

		verify(policyRecommendationRepository).recordDecision(eq(RECOMMENDATION_ID),
				eq(RecommendationStatus.REJECTED), eq(DecisionResult.HOLD), eq("추가 검증 필요"), eq(approver), any());
	}

	@Test
	@DisplayName("시스템 제안과 다른 결정인데 reason이 비어 있으면 DECISION_REASON_REQUIRED")
	void reasonRequiredWhenAgainstProposal() {
		assertThatThrownBy(() -> service.recordDecision(USER_ID,
				new DecisionRecordRequest(RECOMMENDATION_ID, DecisionResult.HOLD, "  ")))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.DECISION_REASON_REQUIRED);

		verify(policyRecommendationRepository, never())
				.recordDecision(anyLong(), any(), any(), any(), any(), any());
		verifyNoInteractions(auditLogRepository);
	}

	@Test
	@DisplayName("시스템 제안(next_action)이 없으면 어떤 결정이든 reason이 필요하다")
	void reasonRequiredWhenNoProposal() {
		given(policyRecommendationRepository.findWithPolicyById(RECOMMENDATION_ID))
				.willReturn(Optional.of(recommendation(company, null)));

		assertThatThrownBy(() -> service.recordDecision(USER_ID,
				new DecisionRecordRequest(RECOMMENDATION_ID, DecisionResult.EXPAND, null)))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.DECISION_REASON_REQUIRED);
	}

	@Test
	@DisplayName("책임자(APPROVER)가 아니면 DECISION_PERMISSION_DENIED")
	void notApprover() {
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(company, RoleType.ADMIN)));

		assertThatThrownBy(() -> service.recordDecision(USER_ID,
				new DecisionRecordRequest(RECOMMENDATION_ID, DecisionResult.EXPAND, null)))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.DECISION_PERMISSION_DENIED);

		verifyNoInteractions(policyRecommendationRepository, auditLogRepository);
	}

	@Test
	@DisplayName("추천이 없으면 RECOMMENDATION_NOT_FOUND")
	void recommendationNotFound() {
		given(policyRecommendationRepository.findWithPolicyById(RECOMMENDATION_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.recordDecision(USER_ID,
				new DecisionRecordRequest(RECOMMENDATION_ID, DecisionResult.EXPAND, null)))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.RECOMMENDATION_NOT_FOUND);
	}

	@Test
	@DisplayName("다른 회사의 추천이면 RECOMMENDATION_ACCESS_DENIED")
	void otherCompany() {
		given(policyRecommendationRepository.findWithPolicyById(RECOMMENDATION_ID))
				.willReturn(Optional.of(recommendation(company(99L), "EXPAND")));

		assertThatThrownBy(() -> service.recordDecision(USER_ID,
				new DecisionRecordRequest(RECOMMENDATION_ID, DecisionResult.EXPAND, null)))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.RECOMMENDATION_ACCESS_DENIED);

		verify(policyRecommendationRepository, never())
				.recordDecision(anyLong(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("갱신된 행이 0이면(이미 결정됨) RECOMMENDATION_ALREADY_DECIDED, 감사 로그는 남기지 않는다")
	void alreadyDecided() {
		given(policyRecommendationRepository.recordDecision(anyLong(), any(), any(), any(), any(), any()))
				.willReturn(0);

		assertThatThrownBy(() -> service.recordDecision(USER_ID,
				new DecisionRecordRequest(RECOMMENDATION_ID, DecisionResult.EXPAND, null)))
				.isInstanceOf(CustomException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.RECOMMENDATION_ALREADY_DECIDED);

		verifyNoInteractions(auditLogRepository);
	}

	private Company company(Long id) {
		Company company = Company.builder().companyCode("C-" + id).companyName("회사").build();
		ReflectionTestUtils.setField(company, "id", id);
		return company;
	}

	private User user(Company company, RoleType roleType) {
		User user = User.builder()
				.company(company)
				.employeeNumber("E-1")
				.passwordHash("hash")
				.userName("책임자")
				.roleType(roleType)
				.failedLoginCount(0)
				.build();
		ReflectionTestUtils.setField(user, "id", USER_ID);
		return user;
	}

	private PolicyRecommendation recommendation(Company company, String nextAction) {
		Policy policy = Policy.builder().company(company).taskType("비용 정산").build();
		ReflectionTestUtils.setField(policy, "id", POLICY_ID);
		PolicyRecommendation recommendation = PolicyRecommendation.builder()
				.policy(policy)
				.status(RecommendationStatus.PENDING)
				.nextAction(nextAction)
				.sampleCount(10)
				.build();
		ReflectionTestUtils.setField(recommendation, "id", RECOMMENDATION_ID);
		return recommendation;
	}
}
