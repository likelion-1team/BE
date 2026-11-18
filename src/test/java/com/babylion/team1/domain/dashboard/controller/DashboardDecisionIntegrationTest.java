package com.babylion.team1.domain.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.audit.entity.AuditEventType;
import com.babylion.team1.domain.audit.entity.AuditLog;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.company.repository.CompanyRepository;
import com.babylion.team1.domain.policy.entity.DecisionResult;
import com.babylion.team1.domain.policy.entity.Policy;
import com.babylion.team1.domain.policy.entity.PolicyRecommendation;
import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import com.babylion.team1.domain.policy.repository.PolicyRecommendationRepository;
import com.babylion.team1.domain.policy.repository.PolicyRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.JwtTokens;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * POST /api/v1/dashboard/decisions (대시보드 결과 기록 저장) 통합 테스트.
 * 실제 DB(docker compose)를 사용하며, 테스트마다 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardDecisionIntegrationTest {

	private static final String URL = "/api/v1/dashboard/decisions";

	/** JWT_SECRET_BASE64가 없는 테스트 환경에서도 토큰을 발급·검증할 수 있도록 테스트 전용 임의 키를 넣는다. */
	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		String secret = Base64.getEncoder().encodeToString(key);
		registry.add("jwt.secret", () -> secret);
	}

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtTokens jwtTokens;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private CompanyRepository companyRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PolicyRepository policyRepository;
	@Autowired
	private PolicyRecommendationRepository policyRecommendationRepository;
	@Autowired
	private AuditLogRepository auditLogRepository;

	private String suffix;
	private Company company;
	private User approver;
	private Policy policy;
	private PolicyRecommendation recommendation;

	@BeforeEach
	void setUp() {
		suffix = UUID.randomUUID().toString().substring(0, 8);
		company = saveCompany("DEC-");
		approver = saveUser(company, RoleType.APPROVER, "A-");
		policy = savePolicy(company, approver);
		recommendation = saveRecommendation(policy, "EXPAND", null);
	}

	@Test
	@DisplayName("시스템 제안과 같은 결정을 기록하면 S200, 추천 갱신과 감사 로그 저장이 함께 이루어진다")
	void recordDecision() throws Exception {
		String body = perform(approver, json(recommendation.getId(), "EXPAND", "전사 확대 결정"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("S200"))
				.andExpect(jsonPath("$.message").value("의사결정이 성공적으로 기록되었습니다."))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.data.auditId").isNumber())
				.andExpect(jsonPath("$.data.recommendationId").value(recommendation.getId()))
				.andExpect(jsonPath("$.data.decision").value("EXPAND"))
				.andExpect(jsonPath("$.data.decidedAt").value(
						org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00")))
				.andReturn().getResponse().getContentAsString();
		long auditId = objectMapper.readTree(body).get("data").get("auditId").asLong();

		entityManager.flush();
		entityManager.clear();

		PolicyRecommendation updated = policyRecommendationRepository.findById(recommendation.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(RecommendationStatus.APPROVED);
		assertThat(updated.getDecisionResult()).isEqualTo(DecisionResult.EXPAND);
		assertThat(updated.getDecidedBy().getId()).isEqualTo(approver.getId());
		assertThat(updated.getDecidedAt()).isNotNull();
		assertThat(updated.getRejectionReason()).isNull();

		AuditLog auditLog = auditLogRepository.findById(auditId).orElseThrow();
		assertThat(auditLog.getEventType()).isEqualTo(AuditEventType.DECISION_RECORDED);
		assertThat(auditLog.getUser().getId()).isEqualTo(approver.getId());
		JsonNode detail = objectMapper.readTree(auditLog.getDetail());
		assertThat(detail.get("recommendationId").asLong()).isEqualTo(recommendation.getId());
		assertThat(detail.get("policyId").asLong()).isEqualTo(policy.getId());
		assertThat(detail.get("systemProposal").asString()).isEqualTo("EXPAND");
		assertThat(detail.get("decision").asString()).isEqualTo("EXPAND");
		assertThat(detail.get("reason").asString()).isEqualTo("전사 확대 결정");
	}

	@Test
	@DisplayName("시스템 제안과 다른 결정이면 REJECTED로 기록되고 reason이 rejection_reason에 저장된다")
	void recordDecisionAgainstProposal() throws Exception {
		perform(approver, json(recommendation.getId(), "HOLD", "추가 검증 필요"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.decision").value("HOLD"));

		entityManager.flush();
		entityManager.clear();

		PolicyRecommendation updated = policyRecommendationRepository.findById(recommendation.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(RecommendationStatus.REJECTED);
		assertThat(updated.getDecisionResult()).isEqualTo(DecisionResult.HOLD);
		assertThat(updated.getRejectionReason()).isEqualTo("추가 검증 필요");
	}

	@Test
	@DisplayName("시스템 제안과 다른 결정인데 reason이 없으면 400 DECISION_REASON_REQUIRED")
	void reasonRequired() throws Exception {
		perform(approver, json(recommendation.getId(), "HOLD", null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DECISION_REASON_REQUIRED"));
	}

	@Test
	@DisplayName("허용되지 않은 decision 값이면 400")
	void invalidDecision() throws Exception {
		perform(approver, "{\"recommendationId\":" + recommendation.getId() + ",\"decision\":\"DELETE\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("recommendationId 또는 decision이 없으면 400 INVALID_REQUEST")
	void missingRequiredFields() throws Exception {
		perform(approver, "{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.errors.length()").value(2));
	}

	@Test
	@DisplayName("토큰이 없으면 401")
	void unauthorized() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
						.content(json(recommendation.getId(), "EXPAND", null)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("책임자(APPROVER)가 아니면 403 DECISION_PERMISSION_DENIED")
	void notApprover() throws Exception {
		User normalUser = saveUser(company, RoleType.USER, "U-");

		perform(normalUser, json(recommendation.getId(), "EXPAND", null))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("DECISION_PERMISSION_DENIED"));
	}

	@Test
	@DisplayName("다른 회사 책임자가 요청하면 403 RECOMMENDATION_ACCESS_DENIED")
	void otherCompanyApprover() throws Exception {
		User otherApprover = saveUser(saveCompany("OTH-"), RoleType.APPROVER, "O-");

		perform(otherApprover, json(recommendation.getId(), "EXPAND", null))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("RECOMMENDATION_ACCESS_DENIED"));
	}

	@Test
	@DisplayName("존재하지 않는 recommendationId면 404 RECOMMENDATION_NOT_FOUND")
	void recommendationNotFound() throws Exception {
		perform(approver, json(Long.MAX_VALUE, "EXPAND", null))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
	}

	@Test
	@DisplayName("이미 decided_at이 기록된 추천이면 409 RECOMMENDATION_ALREADY_DECIDED")
	void alreadyDecided() throws Exception {
		PolicyRecommendation decided = saveRecommendation(policy, "EXPAND", LocalDateTime.now());

		perform(approver, json(decided.getId(), "EXPAND", null))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RECOMMENDATION_ALREADY_DECIDED"));
	}

	@Test
	@DisplayName("같은 추천에 두 번 결정하면 두 번째 요청은 409")
	void secondDecisionConflicts() throws Exception {
		perform(approver, json(recommendation.getId(), "EXPAND", null)).andExpect(status().isOk());

		perform(approver, json(recommendation.getId(), "HOLD", "번복"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RECOMMENDATION_ALREADY_DECIDED"));
	}

	private ResultActions perform(User user, String body) throws Exception {
		String accessToken = jwtTokens.issue(user.getId()).accessToken();
		return mockMvc.perform(post(URL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private String json(Long recommendationId, String decision, String reason) {
		String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
		return "{\"recommendationId\":" + recommendationId
				+ ",\"decision\":\"" + decision + "\""
				+ ",\"reason\":" + reasonJson + "}";
	}

	private Company saveCompany(String prefix) {
		return companyRepository.save(
				Company.builder().companyCode(prefix + suffix).companyName("결정 테스트").build());
	}

	private User saveUser(Company company, RoleType roleType, String prefix) {
		return userRepository.save(User.builder()
				.company(company)
				.employeeNumber(prefix + suffix)
				.passwordHash("hash")
				.userName("테스터")
				.roleType(roleType)
				.failedLoginCount(0)
				.build());
	}

	private Policy savePolicy(Company company, User createdBy) {
		return policyRepository.save(
				Policy.builder().company(company).taskType("비용 정산").createdBy(createdBy).build());
	}

	private PolicyRecommendation saveRecommendation(Policy policy, String nextAction, LocalDateTime decidedAt) {
		return policyRecommendationRepository.save(PolicyRecommendation.builder()
				.policy(policy)
				.status(RecommendationStatus.PENDING)
				.nextAction(nextAction)
				.decidedAt(decidedAt)
				.sampleCount(10)
				.build());
	}
}
