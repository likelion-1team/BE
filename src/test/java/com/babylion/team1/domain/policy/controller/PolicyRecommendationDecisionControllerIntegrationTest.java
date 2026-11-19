package com.babylion.team1.domain.policy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.company.repository.CompanyRepository;
import com.babylion.team1.domain.policy.entity.Policy;
import com.babylion.team1.domain.policy.entity.PolicyRecommendation;
import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import com.babylion.team1.domain.policy.repository.PolicyRecommendationRepository;
import com.babylion.team1.domain.policy.repository.PolicyRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.JwtTokens;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyRecommendationDecisionControllerIntegrationTest {
	private static final String URL = "/api/v1/policy-recommendations/";
	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) {
		byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
		String secret = Base64.getEncoder().encodeToString(key);
		registry.add("jwt.secret", () -> secret);
	}
	@Autowired MockMvc mvc;
	@Autowired JwtTokens tokens;
	@Autowired ObjectMapper mapper;
	@Autowired EntityManager em;
	@Autowired JdbcTemplate jdbc;
	@Autowired CompanyRepository companies;
	@Autowired UserRepository users;
	@Autowired PolicyRepository policies;
	@Autowired PolicyRecommendationRepository recommendations;
	@Autowired PolicyVersionRepository versions;
	private Company company;
	private User approver;
	private User otherApprover;
	private Policy policy;
	private PolicyRecommendation pending;
	private PolicyRecommendation foreign;
	private PolicyVersion base;

	@BeforeEach
	void fixture() {
		company = companies.save(Company.builder().companyCode("RD-" + UUID.randomUUID()).companyName("추천 결정 테스트").build());
		var other = companies.save(Company.builder().companyCode("RD-OTHER-" + UUID.randomUUID()).companyName("다른 회사").build());
		approver = user(company, RoleType.APPROVER);
		otherApprover = user(other, RoleType.APPROVER);
		policy = policies.save(Policy.builder().company(company).createdBy(approver).taskType("INVOICE").build());
		var otherPolicy = policies.save(Policy.builder().company(other).createdBy(otherApprover).taskType("SUPPORT").build());
		pending = recommendation(policy, RecommendationStatus.PENDING, null);
		foreign = recommendation(otherPolicy, RecommendationStatus.PENDING, null);
		base = versions.saveAndFlush(PolicyVersion.builder().policy(policy).createdBy(approver).versionName("existing")
				.status(PolicyVersionStatus.ACTIVE).allowedTools("[\"ERP\"]").build());
		em.clear();
	}

	@Test
	void approvalCreatesLinkedDraftAndBothAuditsWithoutChangingExistingVersionOrMappings() throws Exception {
		var original = jdbc.queryForMap("select * from policy_version where version_id = ?", base.getId());
		long mappings = jdbc.queryForObject("select count(*) from project_policy_mapping", Long.class);
		var body = minimal();
		body.put("confidenceThreshold", 85.25).put("successDefinition", "검토 통과").put("defaultRoute", "AI_ERP_DIRECT");
		body.set("qualityTimeCriteria", mapper.readTree("{\"seconds\":30}"));
		body.set("exceptionConditions", mapper.readTree("[\"manual\"]"));
		body.put("status", "ACTIVE").put("createdBy", otherApprover.getId()).put("decidedBy", otherApprover.getId())
				.put("policyId", foreign.getPolicy().getId()).put("generatedVersionId", base.getId());
		var result = request(pending.getId(), "approve", body, approver).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("S200"))
				.andExpect(jsonPath("$.message").value("정책 추천 승인 처리에 성공했습니다"))
				.andExpect(jsonPath("$.data.recommendationId").value(pending.getId()))
				.andExpect(jsonPath("$.data.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.policyId").value(policy.getId()))
				.andExpect(jsonPath("$.data.versionStatus").value("DRAFT"))
				.andExpect(jsonPath("$.data.decidedBy").value(approver.getId())).andReturn();
		long versionId = mapper.readTree(result.getResponse().getContentAsString()).path("data").path("versionId").asLong();
		em.flush(); em.clear();
		assertThat(versionId).isNotEqualTo(base.getId());
		var row = jdbc.queryForMap("select * from policy_version where version_id = ?", versionId);
		assertThat(row).containsEntry("policy_id", policy.getId()).containsEntry("status", "DRAFT")
				.containsEntry("created_by", approver.getId()).containsEntry("approved_by", null)
				.containsEntry("applied_at", null).containsEntry("rolled_back_at", null).containsEntry("rollback_reason", null);
		assertThat(mapper.readTree((String) row.get("allowed_tools"))).isEqualTo(mapper.readTree("[\"AI\",\"ERP\"]"));
		assertThat(jdbc.queryForObject("select generated_version_id from policy_recommendation where recommendation_id = ?", Long.class, pending.getId())).isEqualTo(versionId);
		var returnedTime = LocalDateTime.parse(mapper.readTree(result.getResponse().getContentAsString()).path("data").path("decidedAt").asString());
		assertThat(jdbc.queryForObject("select decided_at from policy_recommendation where recommendation_id = ?", java.sql.Timestamp.class, pending.getId()).toLocalDateTime()).isEqualTo(returnedTime);
		assertThat(jdbc.queryForMap("select * from policy_version where version_id = ?", base.getId())).isEqualTo(original);
		assertThat(jdbc.queryForObject("select count(*) from project_policy_mapping", Long.class)).isEqualTo(mappings);
		assertThat(events()).containsExactlyInAnyOrder("POLICY_CREATED", "RECOMMENDATION_APPROVED");
		var audit = mapper.readTree(jdbc.queryForObject("select detail from audit_log where user_id = ? and event_type = 'RECOMMENDATION_APPROVED'", String.class, approver.getId()));
		assertThat(audit.path("recommendationId").asLong()).isEqualTo(pending.getId());
		assertThat(audit.path("policyId").asLong()).isEqualTo(policy.getId());
		assertThat(audit.path("versionId").asLong()).isEqualTo(versionId);
		mvc.perform(get(URL + pending.getId()).header(HttpHeaders.AUTHORIZATION, bearer(approver))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.versionId").value(versionId));
		mvc.perform(get("/api/v1/policies/" + policy.getId() + "/versions/" + versionId).header(HttpHeaders.AUTHORIZATION, bearer(approver)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"))
				.andExpect(jsonPath("$.data.qualityTimeCriteria.seconds").value(30));
	}

	@Test
	void rejectionStripsReasonAndRecordsOnlyRejectionAudit() throws Exception {
		request(pending.getId(), "reject", Map.of("rejectionReason", " \n현재 기준과 다름\t "), approver)
				.andExpect(status().isOk()).andExpect(jsonPath("$.message").value("정책 추천 반려 처리에 성공했습니다"))
				.andExpect(jsonPath("$.data.status").value("REJECTED"))
				.andExpect(jsonPath("$.data.rejectionReason").value("현재 기준과 다름"))
				.andExpect(jsonPath("$.data.decidedBy").value(approver.getId()));
		assertThat(versionCount()).isEqualTo(1);
		assertThat(events()).containsExactly("RECOMMENDATION_REJECTED");
		var row = jdbc.queryForMap("select * from policy_recommendation where recommendation_id = ?", pending.getId());
		assertThat(row).containsEntry("status", "REJECTED").containsEntry("decided_by", approver.getId())
				.containsEntry("rejection_reason", "현재 기준과 다름").containsEntry("generated_version_id", null);
		assertThat(row.get("decided_at")).isNotNull();
	}

	@Test
	void omittedOptionalSettingsStayNullAndDoNotCopyRecommendationDetail() throws Exception {
		var result = request(pending.getId(), "approve", minimal(), approver).andExpect(status().isOk()).andReturn();
		long id = mapper.readTree(result.getResponse().getContentAsString()).path("data").path("versionId").asLong();
		var row = jdbc.queryForMap("select * from policy_version where version_id = ?", id);
		for (String field : List.of("confidence_threshold", "success_definition", "quality_time_criteria", "exception_conditions", "default_route")) {
			assertThat(row.get(field)).as(field).isNull();
		}
	}

	@Test
	void userAndAdminCannotApproveOrReject() throws Exception {
		for (var role : List.of(RoleType.USER, RoleType.ADMIN)) {
			var actor = user(company, role);
			for (String operation : List.of("approve", "reject")) {
				request(pending.getId(), operation, body(operation), actor).andExpect(status().isForbidden())
						.andExpect(jsonPath("$.code").value("DECISION_PERMISSION_DENIED"));
			}
		}
		assertUnchanged();
	}

	@Test
	void foreignAndMissingRecommendationsUseTheSame404() throws Exception {
		for (long id : List.of(foreign.getId(), Long.MAX_VALUE)) {
			for (String operation : List.of("approve", "reject")) {
				request(id, operation, body(operation), approver).andExpect(status().isNotFound())
						.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
			}
		}
		assertUnchanged();
	}

	@Test
	void alreadyDecidedAndInconsistentRecordsCannotBeProcessed() throws Exception {
		var approved = recommendation(policy, RecommendationStatus.APPROVED, null);
		var rejected = recommendation(policy, RecommendationStatus.REJECTED, LocalDateTime.now());
		var timed = recommendation(policy, RecommendationStatus.PENDING, LocalDateTime.now());
		var linked = recommendation(policy, RecommendationStatus.PENDING, null);
		em.flush();
		jdbc.update("update policy_recommendation set generated_version_id = ? where recommendation_id = ?", base.getId(), linked.getId());
		em.clear();
		for (var r : List.of(approved, rejected, timed, linked)) {
			for (String operation : List.of("approve", "reject")) {
				request(r.getId(), operation, body(operation), approver).andExpect(status().isConflict())
						.andExpect(jsonPath("$.code").value("RECOMMENDATION_ALREADY_DECIDED"));
			}
		}
		assertUnchanged();
	}

	@Test
	void repeatsAndOppositeDecisionsAre409WithoutExtraVersionsOrAudits() throws Exception {
		request(pending.getId(), "approve", minimal(), approver).andExpect(status().isOk());
		for (String operation : List.of("approve", "reject")) {
			request(pending.getId(), operation, body(operation), approver).andExpect(status().isConflict());
		}
		var rejectTarget = recommendation(policy, RecommendationStatus.PENDING, null);
		request(rejectTarget.getId(), "reject", body("reject"), approver).andExpect(status().isOk());
		for (String operation : List.of("approve", "reject")) {
			request(rejectTarget.getId(), operation, body(operation), approver).andExpect(status().isConflict());
		}
		assertThat(versionCount()).isEqualTo(2);
		assertThat(events()).containsExactlyInAnyOrder("POLICY_CREATED", "RECOMMENDATION_APPROVED", "RECOMMENDATION_REJECTED");
	}

	@Test
	void dashboardFirstPreventsLaterPolicyDecisionAndCreatesNoDraft() throws Exception {
		dashboard(pending.getId()).andExpect(status().isOk());
		for (String operation : List.of("approve", "reject")) {
			request(pending.getId(), operation, body(operation), approver).andExpect(status().isConflict());
		}
		assertThat(versionCount()).isEqualTo(1);
		assertThat(events()).containsExactly("DECISION_RECORDED");
	}

	@Test
	void policyFirstPreventsLaterDashboardDecision() throws Exception {
		request(pending.getId(), "approve", minimal(), approver).andExpect(status().isOk());
		dashboard(pending.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECOMMENDATION_ALREADY_DECIDED"));
		assertThat(versionCount()).isEqualTo(2);
		assertThat(events()).containsExactlyInAnyOrder("POLICY_CREATED", "RECOMMENDATION_APPROVED");
	}

	@Test
	void approvalRequiredFieldsNameDecimalAndTextBoundsAreValidatedBeforeWriting() throws Exception {
		var invalid = new ArrayList<ObjectNode>();
		invalid.add(mapper.createObjectNode());
		var noTools = minimal(); noTools.remove("allowedTools"); invalid.add(noTools);
		var nullTools = minimal(); nullTools.putNull("allowedTools"); invalid.add(nullTools);
		for (String name : List.of("", " \t\n", "x".repeat(51))) {
			var b = minimal(); b.put("versionName", name); invalid.add(b);
		}
		for (String number : List.of("1000.00", "0.001")) {
			var b = minimal(); b.put("confidenceThreshold", new java.math.BigDecimal(number)); invalid.add(b);
		}
		var text = minimal(); text.put("successDefinition", "가".repeat(21845) + "a"); invalid.add(text);
		for (var b : invalid) {
			request(pending.getId(), "approve", b, approver).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		assertUnchanged();
	}

	@Test
	void everyJsonFieldAccepts100NestedContainersAndRejects101() throws Exception {
		for (String field : List.of("allowedTools", "qualityTimeCriteria", "exceptionConditions")) {
			var bad = minimal(); bad.set(field, mapper.readTree("[".repeat(101) + "0" + "]".repeat(101)));
			request(pending.getId(), "approve", bad, approver).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
			var target = recommendation(policy, RecommendationStatus.PENDING, null);
			var good = minimal(); good.set(field, mapper.readTree("[".repeat(100) + "0" + "]".repeat(100)));
			request(target.getId(), "approve", good, approver).andExpect(status().isOk());
		}
	}

	@Test
	void unsupportedJsonNumbersAre400BeforeAnyDecisionOrDraft() throws Exception {
		for (String field : List.of("allowedTools", "qualityTimeCriteria", "exceptionConditions")) {
			for (String number : List.of("9".repeat(400), "-" + "9".repeat(400), "1e309", "-1e309")) {
				var body = minimal(); body.put(field, "__RAW_JSON__");
				String value = number.startsWith("-") ? "{\"nested\":[" + number + "]}" : number;
				String rawBody = mapper.writeValueAsString(body).replace("\"__RAW_JSON__\"", value);
				raw(pending.getId(), "approve", rawBody, approver).andExpect(status().isBadRequest())
						.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
						.andExpect(jsonPath("$.errors[0].reason").value("JSON 숫자는 MySQL JSON에 저장 가능한 유한한 double 범위여야 합니다."));
			}
		}
		assertUnchanged();
	}

	@Test
	void supportedJsonNumberBoundariesAndNumericLookingStringsCanBeStored() throws Exception {
		String largestInteger = new java.math.BigDecimal(Double.MAX_VALUE).toBigInteger().toString();
		for (String field : List.of("allowedTools", "qualityTimeCriteria", "exceptionConditions")) {
			for (String value : List.of(largestInteger, "-" + largestInteger, "1.7976931348623157e308", "-1.7976931348623157e308", "4.9e-324",
					"18446744073709551615", "-9223372036854775808", "0", "\"Infinity\"", "\"" + "9".repeat(400) + "\"")) {
				var target = recommendation(policy, RecommendationStatus.PENDING, null);
				var body = minimal(); body.set(field, mapper.readTree(value));
				var result = request(target.getId(), "approve", body, approver).andExpect(status().isOk()).andReturn();
				long id = mapper.readTree(result.getResponse().getContentAsString()).path("data").path("versionId").asLong();
				String column = switch (field) {
					case "allowedTools" -> "allowed_tools";
					case "qualityTimeCriteria" -> "quality_time_criteria";
					default -> "exception_conditions";
				};
				var stored = mapper.readTree(jdbc.queryForObject("select " + column + " from policy_version where version_id = ?", String.class, id));
				if (value.startsWith("\"")) {
					assertThat(stored.isString()).isTrue();
					assertThat(stored.asString()).isEqualTo(mapper.readTree(value).asString());
				} else {
					assertThat(stored.isNumber()).isTrue();
					// 64비트 밖 정수는 MySQL JSON이 double로 저장하므로 정확한 정수 보존을 요구하지 않는다.
					assertThat(Double.isFinite(stored.numberValue().doubleValue())).isTrue();
					if (!value.equals(largestInteger) && !value.equals("-" + largestInteger)) {
						assertThat(stored.numberValue().doubleValue()).isEqualTo(mapper.readTree(value).numberValue().doubleValue());
					}
				}
			}
		}
	}

	@Test
	void optionalJsonNullAndDatabaseBoundaryValuesAreAccepted() throws Exception {
		var b = minimal(); b.put("versionName", "x".repeat(50)).put("confidenceThreshold", new java.math.BigDecimal("999.99"))
				.put("successDefinition", "가".repeat(21845)).putNull("qualityTimeCriteria").putNull("exceptionConditions");
		request(pending.getId(), "approve", b, approver).andExpect(status().isOk());
	}

	@Test
	void blankMissingNullAndOversizedRejectionReasonsAre400() throws Exception {
		for (String raw : List.of("{}", "{\"rejectionReason\":null}", "{\"rejectionReason\":\"\"}", "{\"rejectionReason\":\" \\t\\n\"}")) {
			raw(pending.getId(), "reject", raw, approver).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		request(pending.getId(), "reject", Map.of("rejectionReason", "가".repeat(21845) + "a"), approver)
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertUnchanged();
	}

	@Test
	void rejectionAcceptsTextByteLimit() throws Exception {
		request(pending.getId(), "reject", Map.of("rejectionReason", "가".repeat(21845)), approver).andExpect(status().isOk());
	}

	@Test
	void malformedBodiesAndEnumUseExistingJsonError() throws Exception {
		for (String operation : List.of("approve", "reject")) {
			for (String raw : List.of("", "{invalid", "[]")) {
				raw(pending.getId(), operation, raw, approver).andExpect(status().isBadRequest())
						.andExpect(jsonPath("$.code").value("INVALID_JSON"));
			}
		}
		var b = minimal(); b.put("defaultRoute", "AI");
		request(pending.getId(), "approve", b, approver).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_JSON"));
		assertUnchanged();
	}

	@Test
	void bearerAndPathValidationCoverBothNewPosts() throws Exception {
		for (String operation : List.of("approve", "reject")) {
			String content = mapper.writeValueAsString(body(operation));
			mvc.perform(post(URL + pending.getId() + "/" + operation).contentType(MediaType.APPLICATION_JSON).content(content))
					.andExpect(status().isUnauthorized());
			for (String token : List.of("invalid", tokens.issue(approver.getId()).refreshToken(), tokens.issue(Long.MAX_VALUE).accessToken())) {
				mvc.perform(post(URL + pending.getId() + "/" + operation).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isUnauthorized());
			}
			for (long id : List.of(0L, -1L)) {
				request(id, operation, body(operation), approver).andExpect(status().isBadRequest())
						.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
			}
			mvc.perform(post(URL + "abc/" + operation).header(HttpHeaders.AUTHORIZATION, bearer(approver))
					.contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_TYPE"));
		}
	}

	@Test
	void swaggerDocumentsBothWriteContractsAndReloadableVersionId() throws Exception {
		var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		var doc = mapper.readTree(result.getResponse().getContentAsString());
		for (String operation : List.of("approve", "reject")) {
			var post = doc.path("paths").path(URL + "{recommendationId}/" + operation).path("post");
			assertThat(post.path("security").get(0).has("bearerAuth")).isTrue();
			assertThat(post.path("requestBody").path("required").asBoolean()).isTrue();
		}
		var schemas = doc.path("components").path("schemas");
		var required = new ArrayList<String>();
		for (var value : schemas.path("PolicyRecommendationApproveRequest").path("required")) { required.add(value.asString()); }
		assertThat(required).containsExactlyInAnyOrder("versionName", "allowedTools");
		assertThat(schemas.path("PolicyRecommendationApproveRequest").path("properties").has("successDefinitionSupported")).isFalse();
		assertThat(schemas.path("PolicyRecommendationApproveRequest").path("properties").has("draftRequest")).isFalse();
		for (String field : List.of("allowedTools", "qualityTimeCriteria", "exceptionConditions")) {
			assertThat(schemas.path("PolicyRecommendationApproveRequest").path("properties").path(field).path("description").asString())
					.contains("JSON 숫자는 유한한 double 범위");
		}
		assertThat(schemas.path("PolicyRecommendationRejectRequest").path("properties").has("rejectionReasonSupported")).isFalse();
		assertThat(schemas.path("PolicyRecommendationDetailResponse").path("properties").has("versionId")).isTrue();
		for (String field : List.of("recommendationId", "status", "decidedBy", "decidedAt", "policyId", "versionId", "versionStatus")) {
			assertThat(schemas.path("PolicyRecommendationApproveResponse").path("properties").has(field)).as(field).isTrue();
		}
	}

	private ObjectNode minimal() {
		var b = mapper.createObjectNode(); b.put("versionName", "recommendation-draft");
		b.set("allowedTools", mapper.readTree("[\"AI\",\"ERP\"]")); return b;
	}
	private Object body(String operation) { return operation.equals("approve") ? minimal() : Map.of("rejectionReason", "검토 필요"); }
	private ResultActions request(long id, String operation, Object body, User actor) throws Exception { return raw(id, operation, mapper.writeValueAsString(body), actor); }
	private ResultActions raw(long id, String operation, String body, User actor) throws Exception {
		return mvc.perform(post(URL + id + "/" + operation).header(HttpHeaders.AUTHORIZATION, bearer(actor)).contentType(MediaType.APPLICATION_JSON).content(body));
	}
	private ResultActions dashboard(long id) throws Exception {
		return mvc.perform(post("/api/v1/dashboard/decisions").header(HttpHeaders.AUTHORIZATION, bearer(approver)).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("recommendationId", id, "decision", "EXPAND"))));
	}
	private String bearer(User actor) { return "Bearer " + tokens.issue(actor.getId()).accessToken(); }
	private long versionCount() { return jdbc.queryForObject("select count(*) from policy_version where policy_id = ?", Long.class, policy.getId()); }
	private List<String> events() { return jdbc.queryForList("select event_type from audit_log where user_id = ?", String.class, approver.getId()); }
	private void assertUnchanged() {
		assertThat(versionCount()).isEqualTo(1); assertThat(events()).isEmpty();
		assertThat(jdbc.queryForObject("select status from policy_recommendation where recommendation_id = ?", String.class, pending.getId())).isEqualTo("PENDING");
	}
	private User user(Company owner, RoleType role) {
		return users.saveAndFlush(User.builder().company(owner).employeeNumber(UUID.randomUUID().toString()).userName("추천 결정")
				.passwordHash("test-only").roleType(role).failedLoginCount(0).build());
	}
	private PolicyRecommendation recommendation(Policy owner, RecommendationStatus status, LocalDateTime decidedAt) {
		return recommendations.saveAndFlush(PolicyRecommendation.builder().policy(owner).status(status).nextAction("EXPAND")
				.detail("{\"allowedTools\":[\"SOURCE\"]}").decidedAt(decidedAt).sampleCount(12).build());
	}
}
