package com.babylion.team1.domain.policy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/** 실제 MySQL에서 조회하며 전용 fixture는 테스트 종료 시 모두 롤백한다. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyRecommendationControllerIntegrationTest {
	private static final String URL = "/api/v1/policy-recommendations";
	private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 1, 10, 0);
	private static final LocalDateTime DECIDED = CREATED.plusDays(1);

	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		String secret = Base64.getEncoder().encodeToString(key);
		registry.add("jwt.secret", () -> secret);
	}

	@Autowired private MockMvc mvc;
	@Autowired private JwtTokens tokens;
	@Autowired private CompanyRepository companies;
	@Autowired private UserRepository users;
	@Autowired private PolicyRepository policies;
	@Autowired private PolicyRecommendationRepository recommendations;
	@Autowired private EntityManager entityManager;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;

	private Company company;
	private User admin;
	private User foreignAdmin;
	private Policy invoice;
	private Policy support;
	private Policy emptyPolicy;
	private PolicyRecommendation pending;
	private PolicyRecommendation approved;
	private PolicyRecommendation rejected;
	private PolicyRecommendation foreign;

	@BeforeEach
	void fixture() {
		String suffix = UUID.randomUUID().toString();
		company = companies.save(Company.builder().companyCode("REC-A-" + suffix).companyName("추천 테스트 A").build());
		var otherCompany = companies.save(Company.builder().companyCode("REC-B-" + suffix).companyName("추천 테스트 B").build());
		admin = user(company, RoleType.ADMIN);
		foreignAdmin = user(otherCompany, RoleType.ADMIN);
		invoice = policy(company, admin, "INVOICE");
		support = policy(company, admin, "SUPPORT");
		emptyPolicy = policy(company, admin, "EMPTY");
		var foreignPolicy = policy(otherCompany, foreignAdmin, "INVOICE");
		pending = recommendation(invoice, RecommendationStatus.PENDING, null, null, null, null, 0);
		approved = recommendation(invoice, RecommendationStatus.APPROVED,
				"{\"expectedChange\":{\"rate\":0.1234567890123456789},\"restore\":[true,null]}", admin, DECIDED, null, 12);
		rejected = recommendation(support, RecommendationStatus.REJECTED, "[\"ERP\",{\"enabled\":false}]", admin, DECIDED, "보류 사유", 7);
		foreign = recommendation(foreignPolicy, RecommendationStatus.PENDING, "{}", null, null, null, 99);
		entityManager.flush();
		for (var r : List.of(pending, approved, rejected, foreign)) {
			jdbc.update("update policy_recommendation set created_at = ? where recommendation_id = ?",
					Timestamp.valueOf(r == pending ? CREATED.minusDays(1) : CREATED), r.getId());
		}
		entityManager.clear();
	}

	@Test
	void defaultsReturnAllCompanyStatusesWithoutProjectMembership() throws Exception {
		mvc.perform(auth(get(URL), admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("S200"))
				.andExpect(jsonPath("$.message").value("정책 변경 추천 리스트 조회에 성공했습니다"))
				.andExpect(jsonPath("$.errors").value(nullValue()))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.items[*].recommendationId")
						.value(contains(rejected.getId().intValue(), approved.getId().intValue(), pending.getId().intValue())))
				.andExpect(jsonPath("$.data.items[*].status").value(contains("REJECTED", "APPROVED", "PENDING")))
				.andExpect(jsonPath("$.data.items[0].policyId").value(support.getId()))
				.andExpect(jsonPath("$.data.items[0].taskType").value("SUPPORT"))
				.andExpect(jsonPath("$.data.items[0].observedProblem").value("불필요 호출 증가"))
				.andExpect(jsonPath("$.data.items[0].sampleCount").value(7))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-01T10:00:00"));
	}

	@Test
	void policyAndEveryStatusFilterHaveMatchingCounts() throws Exception {
		for (var r : List.of(pending, approved, rejected)) {
			mvc.perform(auth(get(URL).param("status", r.getStatus().name()), admin)).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalElements").value(1))
					.andExpect(jsonPath("$.data.items[0].recommendationId").value(r.getId()));
		}
		mvc.perform(auth(get(URL).param("policyId", invoice.getId().toString()), admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2));
		mvc.perform(auth(get(URL).param("policyId", invoice.getId().toString()).param("status", "APPROVED").param("size", "1"), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].recommendationId").value(approved.getId()));
		mvc.perform(auth(get(URL).param("policyId", invoice.getId().toString()).param("status", "REJECTED"), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0))
				.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void paginationUsesCreationThenIdOrderAndRetainsTotalBeyondLastPage() throws Exception {
		var ids = List.of(rejected.getId(), approved.getId(), pending.getId());
		for (int page = 0; page < ids.size(); page++) {
			mvc.perform(auth(get(URL).param("page", Integer.toString(page)).param("size", "1"), admin))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(page))
					.andExpect(jsonPath("$.data.size").value(1)).andExpect(jsonPath("$.data.totalElements").value(3))
					.andExpect(jsonPath("$.data.items[0].recommendationId").value(ids.get(page)));
		}
		mvc.perform(auth(get(URL).param("page", "10").param("size", "1"), admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(3)).andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void foreignMissingAndEmptyPolicyFiltersReturnEmptyPages() throws Exception {
		for (long id : List.of(foreign.getPolicy().getId(), Long.MAX_VALUE, emptyPolicy.getId())) {
			mvc.perform(auth(get(URL).param("policyId", Long.toString(id)).param("size", "1"), admin))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
					.andExpect(jsonPath("$.data.totalElements").value(0));
		}
	}

	@Test
	void emptyCompanyCanReadAnEmptyPage() throws Exception {
		var empty = companies.save(Company.builder().companyCode("REC-EMPTY-" + UUID.randomUUID()).companyName("빈 회사").build());
		mvc.perform(auth(get(URL), user(empty, RoleType.ADMIN))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	void pendingDetailPreservesNullsAndDoesNotExposeDecisionResult() throws Exception {
		mvc.perform(auth(get(detail(pending.getId())), admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("S200"))
				.andExpect(jsonPath("$.message").value("정책 추천 아코디언 상세에 성공했습니다"))
				.andExpect(jsonPath("$.data.recommendationId").value(pending.getId()))
				.andExpect(jsonPath("$.data.policyId").value(invoice.getId()))
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.causeCandidate").value("임계값 설정"))
				.andExpect(jsonPath("$.data.nextAction").value("설정 검토"))
				.andExpect(jsonPath("$.data.sampleCount").value(0))
				.andExpect(jsonPath("$.data.detail").value(nullValue()))
				.andExpect(jsonPath("$.data.rejectionReason").value(nullValue()))
				.andExpect(jsonPath("$.data.decidedBy").value(nullValue()))
				.andExpect(jsonPath("$.data.decidedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.decisionResult").doesNotExist())
				.andExpect(jsonPath("$.data.versionId").doesNotExist());
	}

	@Test
	void decidedDetailReturnsJsonStructureActorAndLocalDateTime() throws Exception {
		var result = mvc.perform(auth(get(detail(approved.getId())), admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.detail.expectedChange").isMap())
				.andExpect(jsonPath("$.data.detail.restore").isArray())
				.andExpect(jsonPath("$.data.decidedBy").value(admin.getId()))
				.andExpect(jsonPath("$.data.decidedAt").value("2026-11-02T10:00:00"))
				.andExpect(jsonPath("$.data.createdAt").value("2026-11-01T10:00:00"))
				.andReturn();
		var number = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
				.readTree(result.getResponse().getContentAsString()).path("data").path("detail").path("expectedChange").path("rate");
		var stored = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
				.readTree(jdbc.queryForObject("select detail from policy_recommendation where recommendation_id = ?", String.class, approved.getId()));
		assertThat(number.decimalValue()).isEqualByComparingTo(stored.path("expectedChange").path("rate").decimalValue());
		mvc.perform(auth(get(detail(rejected.getId())), admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.detail").isArray()).andExpect(jsonPath("$.data.detail[0]").value("ERP"))
				.andExpect(jsonPath("$.data.rejectionReason").value("보류 사유"));
	}

	@Test
	void everyValidJsonRootAndSqlNullIsReturnedWithoutStringifying() throws Exception {
		for (String json : List.of("{}", "[]", "null", "true", "42", "\"AI\"")) {
			jdbc.update("update policy_recommendation set detail = ? where recommendation_id = ?", json, pending.getId());
			entityManager.clear();
			var response = mvc.perform(auth(get(detail(pending.getId())), admin)).andExpect(status().isOk()).andReturn();
			assertThat(mapper.readTree(response.getResponse().getContentAsString()).path("data").path("detail"))
					.isEqualTo(mapper.readTree(json));
		}
		jdbc.update("update policy_recommendation set detail = null, observed_problem = null, cause_candidate = null, next_action = null where recommendation_id = ?", pending.getId());
		entityManager.clear();
		mvc.perform(auth(get(detail(pending.getId())), admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.detail").value(nullValue()))
				.andExpect(jsonPath("$.data.observedProblem").value(nullValue()))
				.andExpect(jsonPath("$.data.causeCandidate").value(nullValue()))
				.andExpect(jsonPath("$.data.nextAction").value(nullValue()));
	}

	@Test
	void foreignAndMissingDetailAreTheSame404AndForeignCompanyHasItsOwnData() throws Exception {
		for (long id : List.of(foreign.getId(), Long.MAX_VALUE)) {
			mvc.perform(auth(get(detail(id)), admin)).andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"))
					.andExpect(jsonPath("$.data").value(nullValue()));
		}
		mvc.perform(auth(get(detail(foreign.getId())), foreignAdmin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sampleCount").value(99));
		mvc.perform(auth(get(URL), foreignAdmin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].recommendationId").value(foreign.getId()));
	}

	@Test
	void approverCanReadAndUserCannotReadEitherEndpoint() throws Exception {
		var approver = user(company, RoleType.APPROVER);
		var ordinary = user(company, RoleType.USER);
		for (String path : List.of(URL, detail(pending.getId()))) {
			mvc.perform(auth(get(path), approver)).andExpect(status().isOk());
			mvc.perform(auth(get(path), ordinary)).andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("POLICY_ACCESS_DENIED"));
		}
	}

	@Test
	void missingInvalidRefreshAndDeletedUserTokensAreUnauthorized() throws Exception {
		for (String path : List.of(URL, detail(pending.getId()))) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
			for (String token : List.of("invalid", tokens.issue(admin.getId()).refreshToken(), tokens.issue(Long.MAX_VALUE).accessToken())) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isUnauthorized())
						.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
			}
		}
	}

	@Test
	void invalidPaginationAndNonPositiveIdsAreRejected() throws Exception {
		for (String[] pair : List.of(new String[]{"page", "-1"}, new String[]{"size", "0"}, new String[]{"size", "101"},
				new String[]{"policyId", "0"}, new String[]{"policyId", "-1"})) {
			mvc.perform(auth(get(URL).param(pair[0], pair[1]), admin)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		mvc.perform(auth(get(URL).param("page", "2147483647").param("size", "100"), admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		for (long id : List.of(0L, -1L)) {
			mvc.perform(auth(get(detail(id)), admin)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
	}

	@Test
	void wrongQueryTypesUseValidationErrorAndWrongPathTypesUseTypeError() throws Exception {
		for (String[] pair : List.of(new String[]{"status", "UNKNOWN"}, new String[]{"status", "pending"},
				new String[]{"page", "abc"}, new String[]{"page", "2147483648"}, new String[]{"size", "abc"},
				new String[]{"policyId", "abc"}, new String[]{"policyId", "9223372036854775808"})) {
			mvc.perform(auth(get(URL).param(pair[0], pair[1]), admin)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		for (String id : List.of("abc", "9223372036854775808")) {
			mvc.perform(auth(get(URL + "/" + id), admin)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_TYPE"));
		}
	}

	@Test
	void maximumPageSizeAndSupportedOffsetAreAccepted() throws Exception {
		mvc.perform(auth(get(URL).param("size", "100"), admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.size").value(100));
		mvc.perform(auth(get(URL).param("page", "2147483647").param("size", "1"), admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.totalElements").value(3));
	}

	@Test
	void readsLeaveRecommendationDecisionsAndAuditLogsUnchanged() throws Exception {
		var before = jdbc.queryForList("select * from policy_recommendation where policy_id in (?, ?)", invoice.getId(), support.getId());
		Long auditCount = jdbc.queryForObject("select count(*) from audit_log", Long.class);
		Long versionCount = jdbc.queryForObject("select count(*) from policy_version", Long.class);
		mvc.perform(auth(get(URL), admin)).andExpect(status().isOk());
		for (var r : List.of(pending, approved, rejected)) {
			mvc.perform(auth(get(detail(r.getId())), admin)).andExpect(status().isOk());
		}
		entityManager.flush();
		assertThat(jdbc.queryForList("select * from policy_recommendation where policy_id in (?, ?)", invoice.getId(), support.getId())).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Long.class)).isEqualTo(auditCount);
		assertThat(jdbc.queryForObject("select count(*) from policy_version", Long.class)).isEqualTo(versionCount);
	}

	@Test
	void swaggerDocumentsBothPathsSecurityPaginationAndJsonDetail() throws Exception {
		var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		var doc = mapper.readTree(result.getResponse().getContentAsString());
		for (String path : List.of(URL, URL + "/{recommendationId}")) {
			var operation = doc.path("paths").path(path).path("get");
			assertThat(operation.isMissingNode()).isFalse();
			assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
			for (var parameter : operation.path("parameters")) {
				assertThat(parameter.path("name").asString()).isNotIn("userId", "offsetSupported");
			}
		}
		var parameters = doc.path("paths").path(URL).path("get").path("parameters");
		var names = new ArrayList<String>();
		for (var parameter : parameters) {
			names.add(parameter.path("name").asString());
			if (parameter.path("name").asString().equals("size")) {
				assertThat(parameter.path("schema").path("default").asInt()).isEqualTo(20);
				assertThat(parameter.path("schema").path("maximum").asInt()).isEqualTo(100);
			}
		}
		assertThat(names).containsExactlyInAnyOrder("policyId", "status", "page", "size");
		var schemas = doc.path("components").path("schemas");
		assertThat(schemas.path("PolicyRecommendationListResponse").path("properties").path("items").path("items").path("$ref").asString())
				.isEqualTo("#/components/schemas/PolicyRecommendationListItem");
		for (String field : List.of("recommendationId", "policyId", "taskType", "status", "observedProblem", "sampleCount", "createdAt")) {
			assertThat(schemas.path("PolicyRecommendationListItem").path("properties").has(field)).as(field).isTrue();
		}
		var properties = schemas.path("PolicyRecommendationDetailResponse").path("properties");
		for (String field : List.of("recommendationId", "policyId", "status", "observedProblem", "causeCandidate", "nextAction", "detail",
				"sampleCount", "rejectionReason", "decidedBy", "decidedAt", "createdAt")) {
			assertThat(properties.has(field)).as(field).isTrue();
		}
		assertThat(properties.has("decisionResult")).isFalse();
		var types = new ArrayList<String>();
		for (var type : properties.path("detail").path("type")) { types.add(type.asString()); }
		assertThat(types).containsExactlyInAnyOrder("object", "array", "string", "number", "boolean", "null");
	}

	private String detail(long id) {
		return URL + "/" + id;
	}

	private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, User user) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(user.getId()).accessToken());
	}

	private User user(Company owner, RoleType role) {
		return users.save(User.builder().company(owner).employeeNumber(UUID.randomUUID().toString()).userName("추천 조회 테스트")
				.passwordHash("not-used").roleType(role).failedLoginCount(0).build());
	}

	private Policy policy(Company owner, User creator, String taskType) {
		return policies.save(Policy.builder().company(owner).createdBy(creator).taskType(taskType).build());
	}

	private PolicyRecommendation recommendation(Policy policy, RecommendationStatus status, String detail, User actor,
			LocalDateTime decidedAt, String reason, int sampleCount) {
		return recommendations.save(PolicyRecommendation.builder().policy(policy).status(status).observedProblem("불필요 호출 증가")
				.causeCandidate("임계값 설정").nextAction("설정 검토").detail(detail).decidedBy(actor).decidedAt(decidedAt)
				.rejectionReason(reason).decisionResult(actor == null ? null : DecisionResult.MAINTAIN).sampleCount(sampleCount).build());
	}
}
