package com.babylion.team1.domain.policy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.company.repository.CompanyRepository;
import com.babylion.team1.domain.policy.entity.Policy;
import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.policy.entity.ProjectPolicyMapping;
import com.babylion.team1.domain.policy.repository.PolicyRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository;
import com.babylion.team1.domain.project.entity.Project;
import com.babylion.team1.domain.project.repository.ProjectRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.domain.workrequest.entity.RouteType;
import com.babylion.team1.global.auth.JwtTokens;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.security.SecureRandom;
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

/** 실제 로컬 MySQL을 사용하며 정상·검증 테스트 데이터는 각 테스트 후 롤백한다. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyVersionDraftControllerIntegrationTest {
	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		String secret = Base64.getEncoder().encodeToString(key);
		registry.add("jwt.secret", () -> secret);
	}

	@Autowired MockMvc mvc;
	@Autowired JwtTokens tokens;
	@Autowired CompanyRepository companies;
	@Autowired UserRepository users;
	@Autowired PolicyRepository policies;
	@Autowired PolicyVersionRepository versions;
	@Autowired ProjectRepository projects;
	@Autowired ProjectPolicyMappingRepository mappings;
	@Autowired EntityManager em;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;
	private Company company;
	private User admin;
	private User otherAdmin;
	private Policy policy;
	private Policy otherPolicy;
	private PolicyVersion base;
	private PolicyVersion wrongPolicyVersion;
	private PolicyVersion foreignVersion;

	@BeforeEach
	void setUp() {
		String suffix = UUID.randomUUID().toString();
		company = companies.save(Company.builder().companyCode("PD-A-" + suffix).companyName("초안 A").build());
		var foreign = companies.save(Company.builder().companyCode("PD-B-" + suffix).companyName("초안 B").build());
		admin = user(company, RoleType.ADMIN);
		otherAdmin = user(foreign, RoleType.ADMIN);
		policy = policies.save(Policy.builder().company(company).createdBy(admin).taskType("INVOICE").build());
		var sameCompanyPolicy = policies.save(Policy.builder().company(company).createdBy(admin).taskType("SUPPORT").build());
		otherPolicy = policies.save(Policy.builder().company(foreign).createdBy(otherAdmin).taskType("INVOICE").build());
		base = versions.save(PolicyVersion.builder().policy(policy).createdBy(admin).versionName("base-v1")
				.status(PolicyVersionStatus.ACTIVE).allowedTools("[\"AI\"]").confidenceThreshold(new BigDecimal("80.00"))
				.successDefinition("기준 설정").qualityTimeCriteria("{\"seconds\":30}").exceptionConditions("{}")
				.defaultRoute(RouteType.LLM).approvedBy(admin).appliedAt(LocalDateTime.of(2026, 11, 1, 10, 0)).build());
		wrongPolicyVersion = versions.save(PolicyVersion.builder().policy(sameCompanyPolicy).createdBy(admin).versionName("wrong")
				.status(PolicyVersionStatus.DRAFT).allowedTools("[]").build());
		foreignVersion = versions.save(PolicyVersion.builder().policy(otherPolicy).createdBy(otherAdmin).versionName("foreign")
				.status(PolicyVersionStatus.DRAFT).allowedTools("[]").build());
		var project = projects.save(Project.builder().company(company).projectCode("PD-A").projectName("초안 테스트 프로젝트").build());
		mappings.save(ProjectPolicyMapping.builder().policyVersion(base).project(project).build());
		em.flush();
	}

	@Test
	void createsFullDraftWithAuditAndKeepsOriginalVersionAndMapping() throws Exception {
		var before = jdbc.queryForMap("select * from policy_version where version_id = ?", base.getId());
		long mappingCount = mappings.count();
		var body = minimal();
		body.put("baseVersionId", base.getId());
		body.put("confidenceThreshold", new BigDecimal("85.25"));
		body.put("successDefinition", "검토 통과");
		body.put("defaultRoute", "AI_ERP_DIRECT");
		body.set("allowedTools", mapper.readTree("[\"AI\",\"ERP\"]"));
		body.set("qualityTimeCriteria", mapper.readTree("{\"seconds\":10}"));
		body.set("exceptionConditions", mapper.readTree("[]"));
		var result = create(body, admin).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("S200"))
				.andExpect(jsonPath("$.message").value("새 정책 초안 생성에 성공했습니다"))
				.andExpect(jsonPath("$.data.policyId").value(policy.getId()))
				.andExpect(jsonPath("$.data.status").value("DRAFT"))
				.andExpect(jsonPath("$.data.createdBy").value(admin.getId()))
				.andExpect(jsonPath("$.data.createdAt").exists()).andReturn();
		var data = mapper.readTree(result.getResponse().getContentAsString()).path("data");
		long id = data.path("versionId").longValue();
		assertThat(id).isNotEqualTo(base.getId());
		assertThat(data.path("createdAt").asString()).doesNotContain("+09:00");
		em.clear();
		var saved = versions.findById(id).orElseThrow();
		assertThat(saved.getConfidenceThreshold()).isEqualByComparingTo("85.25");
		assertThat(saved.getApprovedBy()).isNull();
		assertThat(saved.getAppliedAt()).isNull();
		assertThat(saved.getRolledBackAt()).isNull();
		assertThat(saved.getRollbackReason()).isNull();
		assertThat(jdbc.queryForMap("select * from policy_version where version_id = ?", base.getId())).isEqualTo(before);
		assertThat(mappings.count()).isEqualTo(mappingCount);
		var audit = jdbc.queryForMap("select event_type, detail from audit_log where user_id = ?", admin.getId());
		assertThat(audit.get("event_type")).isEqualTo("POLICY_CREATED");
		assertThat(mapper.readTree(audit.get("detail").toString()).path("versionId").longValue()).isEqualTo(id);
		assertThat(mapper.readTree(audit.get("detail").toString()).path("baseVersionId").longValue()).isEqualTo(base.getId());
		mvc.perform(get(url() + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.allowedTools").value(contains("AI", "ERP")))
				.andExpect(jsonPath("$.data.qualityTimeCriteria.seconds").value(10));
		mvc.perform(get(url()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
	}

	@Test
	void omittedSettingsAreNotCopiedFromBase() throws Exception {
		var body = minimal();
		body.put("baseVersionId", base.getId());
		long id = createdId(create(body, admin).andExpect(status().isOk()));
		em.clear();
		var saved = versions.findById(id).orElseThrow();
		assertThat(saved.getAllowedTools()).isEqualTo("[]");
		assertThat(saved.getConfidenceThreshold()).isNull();
		assertThat(saved.getSuccessDefinition()).isNull();
		assertThat(saved.getQualityTimeCriteria()).isNull();
		assertThat(saved.getExceptionConditions()).isNull();
		assertThat(saved.getDefaultRoute()).isNull();
	}

	@Test
	void baseIsOptionalAndOptionalJsonNullBecomesSqlNull() throws Exception {
		var body = minimal();
		body.putNull("qualityTimeCriteria");
		body.putNull("exceptionConditions");
		long id = createdId(create(body, admin).andExpect(status().isOk()));
		assertThat(jdbc.queryForMap("select quality_time_criteria, exception_conditions from policy_version where version_id = ?", id))
				.containsEntry("quality_time_criteria", null).containsEntry("exception_conditions", null);
	}

	@Test
	void authorAndStateComeFromServerInsteadOfBodyMetadata() throws Exception {
		var body = minimal();
		body.put("createdBy", otherAdmin.getId());
		body.put("approvedBy", otherAdmin.getId());
		body.put("status", "ACTIVE");
		body.put("appliedAt", "2026-11-01T10:00:00");
		body.put("policyId", otherPolicy.getId());
		long id = createdId(create(body, admin).andExpect(status().isOk()));
		var row = jdbc.queryForMap("select policy_id, created_by, status, approved_by, applied_at from policy_version where version_id = ?", id);
		assertThat(row).containsEntry("policy_id", policy.getId()).containsEntry("created_by", admin.getId())
				.containsEntry("status", "DRAFT").containsEntry("approved_by", null).containsEntry("applied_at", null);
	}

	@Test
	void approverCanCreateAndUserIsRejectedBeforeWriting() throws Exception {
		create(minimal(), user(company, RoleType.APPROVER)).andExpect(status().isOk());
		long before = versions.count();
		create(minimal(), user(company, RoleType.USER)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("POLICY_DRAFT_ACCESS_DENIED"));
		assertThat(versions.count()).isEqualTo(before);
	}

	@Test
	void missingAndForeignPolicyUseSame404() throws Exception {
		for (long id : List.of(otherPolicy.getId(), Long.MAX_VALUE)) {
			mvc.perform(post("/api/v1/policies/" + id + "/versions").header(HttpHeaders.AUTHORIZATION, bearer(admin))
					.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(minimal())))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
		}
	}

	@Test
	void wrongPolicyForeignOrMissingBaseIsRejectedWithoutDraft() throws Exception {
		long before = versions.count();
		for (long id : List.of(wrongPolicyVersion.getId(), foreignVersion.getId(), Long.MAX_VALUE)) {
			var body = minimal();
			body.put("baseVersionId", id);
			create(body, admin).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_VERSION_NOT_FOUND"));
		}
		assertThat(versions.count()).isEqualTo(before);
	}

	@Test
	void requiredFieldsAndDatabaseBoundsReturn400BeforeWriting() throws Exception {
		long before = versions.count();
		var invalid = new ArrayList<ObjectNode>();
		var missingName = minimal(); missingName.remove("versionName"); invalid.add(missingName);
		var missingTools = minimal(); missingTools.remove("allowedTools"); invalid.add(missingTools);
		var nullTools = minimal(); nullTools.putNull("allowedTools"); invalid.add(nullTools);
		for (String name : List.of("", "   ", "x".repeat(51))) {
			var body = minimal(); body.put("versionName", name); invalid.add(body);
		}
		for (long id : List.of(0L, -1L)) {
			var body = minimal(); body.put("baseVersionId", id); invalid.add(body);
		}
		for (String number : List.of("1000.00", "0.001")) {
			var body = minimal(); body.put("confidenceThreshold", new BigDecimal(number)); invalid.add(body);
		}
		var longText = minimal(); longText.put("successDefinition", "가".repeat(21846)); invalid.add(longText);
		for (var body : invalid) {
			create(body, admin).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		assertThat(versions.count()).isEqualTo(before);
	}

	@Test
	void malformedBodyAndEnumUseExistingJsonError() throws Exception {
		for (String body : List.of("{broken", "", "null", "{\"versionName\":\"v2\",\"allowedTools\":[],\"defaultRoute\":\"AI\"}",
				"{\"versionName\":\"v2\",\"allowedTools\":[],\"baseVersionId\":\"abc\"}")) {
			mvc.perform(post(url()).header(HttpHeaders.AUTHORIZATION, bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
		}
	}

	@Test
	void bearerAndPathValidationAreAppliedToPost() throws Exception {
		mvc.perform(post(url()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(minimal())))
				.andExpect(status().isUnauthorized());
		for (String token : List.of("invalid.token.value", tokens.issue(admin.getId()).refreshToken(), tokens.issue(Long.MAX_VALUE).accessToken())) {
			mvc.perform(post(url()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
					.content(mapper.writeValueAsString(minimal()))).andExpect(status().isUnauthorized());
		}
		for (String id : List.of("0", "-1", "abc")) {
			mvc.perform(post("/api/v1/policies/" + id + "/versions").header(HttpHeaders.AUTHORIZATION, bearer(admin))
					.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(minimal())))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(id.equals("abc") ? "INVALID_TYPE" : "INVALID_REQUEST"));
		}
	}

	@Test
	void duplicateNamesRemainSeparateDrafts() throws Exception {
		long first = createdId(create(minimal(), admin).andExpect(status().isOk()));
		long second = createdId(create(minimal(), admin).andExpect(status().isOk()));
		assertThat(first).isNotEqualTo(second);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where user_id = ?", Long.class, admin.getId())).isEqualTo(2);
	}

	@Test
	void textNameAndDecimalStorageBoundariesAreAccepted() throws Exception {
		var body = minimal();
		body.put("versionName", "x".repeat(50));
		body.put("successDefinition", "가".repeat(21845));
		body.put("confidenceThreshold", new BigDecimal("999.99"));
		long id = createdId(create(body, admin).andExpect(status().isOk()));
		assertThat(jdbc.queryForObject("select octet_length(success_definition) from policy_version where version_id = ?", Integer.class, id)).isEqualTo(65535);
		assertThat(jdbc.queryForObject("select confidence_threshold from policy_version where version_id = ?", BigDecimal.class, id)).isEqualByComparingTo("999.99");
	}

	@Test
	void jsonStructureIsStoredWithoutInventingInnerSchema() throws Exception {
		for (String json : List.of("{}", "[]", "true", "42", "\"AI\"")) {
			var body = minimal(); body.set("allowedTools", mapper.readTree(json));
			long id = createdId(create(body, admin).andExpect(status().isOk()));
			assertThat(mapper.readTree(jdbc.queryForObject("select allowed_tools from policy_version where version_id = ?", String.class, id)))
					.isEqualTo(mapper.readTree(json));
		}
	}

	@Test
	void jsonNestingLimitIsAppliedToEachSettingsFieldBeforeWriting() throws Exception {
		long before = versions.count();
		for (String field : List.of("allowedTools", "qualityTimeCriteria", "exceptionConditions")) {
			for (int depth : List.of(101, 120)) {
				for (boolean object : List.of(false, true)) {
					var body = minimal();
					body.set(field, mapper.readTree(nestedJson(depth, object)));
					create(body, admin).andExpect(status().isBadRequest())
							.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
							.andExpect(jsonPath("$.errors[0].reason").value("JSON의 객체·배열 중첩은 100단계 이하여야 합니다."));
				}
			}
			var mixed = minimal();
			mixed.set(field, mapper.readTree("{\"shallow\":0,\"deep\":" + nestedJson(100, false) + "}"));
			create(mixed, admin).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
					.andExpect(jsonPath("$.errors[0].reason").value("JSON의 객체·배열 중첩은 100단계 이하여야 합니다."));
		}
		assertThat(versions.count()).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where user_id = ?", Long.class, admin.getId())).isZero();
	}

	@Test
	void jsonNesting100CanBeStoredForEachSettingsField() throws Exception {
		for (String field : List.of("allowedTools", "qualityTimeCriteria", "exceptionConditions")) {
			String column = switch (field) {
				case "allowedTools" -> "allowed_tools";
				case "qualityTimeCriteria" -> "quality_time_criteria";
				default -> "exception_conditions";
			};
			for (boolean object : List.of(false, true)) {
				var body = minimal();
				body.set(field, mapper.readTree(nestedJson(100, object)));
				long id = createdId(create(body, admin).andExpect(status().isOk()));
				assertThat(jdbc.queryForObject("select json_depth(" + column + ") from policy_version where version_id = ?", Integer.class, id))
						.isEqualTo(101); // JSON_DEPTH는 컨테이너 100개 외에 마지막 스칼라 값도 센다.
			}
		}
	}

	@Test
	void nestedEmptyContainersAtDepth100AreAccepted() throws Exception {
		for (String json : List.of("[".repeat(99) + "[]" + "]".repeat(99),
				"{\"n\":".repeat(99) + "{}" + "}".repeat(99))) {
			var body = minimal();
			body.set("allowedTools", mapper.readTree(json));
			long id = createdId(create(body, admin).andExpect(status().isOk()));
			assertThat(jdbc.queryForObject("select json_depth(allowed_tools) from policy_version where version_id = ?", Integer.class, id))
					.isEqualTo(100);
		}
	}

	@Test
	void wideJsonIsNotRejectedAsDeepJson() throws Exception {
		var body = minimal();
		var array = mapper.createArrayNode();
		for (int i = 0; i < 150; i++) { array.add(i); }
		body.set("allowedTools", array);
		long id = createdId(create(body, admin).andExpect(status().isOk()));
		assertThat(jdbc.queryForObject("select json_depth(allowed_tools) from policy_version where version_id = ?", Integer.class, id))
				.isEqualTo(2);
	}

	@Test
	void unsupportedJsonNumbersAreRejectedBeforeWriting() throws Exception {
		long before = versions.count();
		for (String field : List.of("allowedTools", "qualityTimeCriteria", "exceptionConditions")) {
			for (String number : List.of("9".repeat(400), "-" + "9".repeat(400), "1e309", "-1e309")) {
				var body = minimal(); body.put(field, "__RAW_JSON__");
				String value = number.startsWith("-") ? "{\"nested\":[" + number + "]}" : number;
				String rawBody = mapper.writeValueAsString(body).replace("\"__RAW_JSON__\"", value);
				mvc.perform(post(url()).header(HttpHeaders.AUTHORIZATION, bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(rawBody))
						.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
						.andExpect(jsonPath("$.errors[0].reason").value("JSON 숫자는 MySQL JSON에 저장 가능한 유한한 double 범위여야 합니다."));
			}
		}
		assertThat(versions.count()).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where user_id = ?", Long.class, admin.getId())).isZero();
	}

	@Test
	void supportedJsonNumberBoundariesAndNumericLookingStringsCanBeStored() throws Exception {
		String largestInteger = new java.math.BigDecimal(Double.MAX_VALUE).toBigInteger().toString();
		for (String field : List.of("allowedTools", "qualityTimeCriteria", "exceptionConditions")) {
			for (String value : List.of(largestInteger, "-" + largestInteger, "1.7976931348623157e308", "-1.7976931348623157e308", "4.9e-324",
					"18446744073709551615", "-9223372036854775808", "0", "\"Infinity\"", "\"" + "9".repeat(400) + "\"")) {
				var body = minimal(); body.set(field, mapper.readTree(value));
				long id = createdId(create(body, admin).andExpect(status().isOk()));
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
	void swaggerIncludesDraftRequestResponseAndPreciseJsonTypes() throws Exception {
		var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		var doc = mapper.readTree(result.getResponse().getContentAsString());
		var operation = doc.path("paths").path("/api/v1/policies/{policyId}/versions").path("post");
		assertThat(operation.isMissingNode()).isFalse();
		assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
		assertThat(operation.path("requestBody").path("required").asBoolean()).isTrue();
		var schemas = doc.path("components").path("schemas");
		var request = schemas.path("PolicyVersionDraftRequest");
		var required = new ArrayList<String>();
		for (var field : request.path("required")) { required.add(field.asString()); }
		assertThat(required).containsExactlyInAnyOrder("versionName", "allowedTools");
		assertThat(request.path("properties").has("successDefinitionSupported")).isFalse();
		var types = new ArrayList<String>();
		for (var type : request.path("properties").path("allowedTools").path("type")) { types.add(type.asString()); }
		assertThat(types).containsExactlyInAnyOrder("object", "array", "string", "number", "boolean");
		for (String field : List.of("allowedTools", "qualityTimeCriteria", "exceptionConditions")) {
			assertThat(request.path("properties").path(field).path("description").asString())
					.contains("객체·배열 중첩 최대 100단계", "요청 본문과 마지막 스칼라는 제외", "JSON 숫자는 유한한 double 범위");
		}
		var response = schemas.path("PolicyVersionDraftResponse").path("properties");
		for (String field : List.of("policyId", "versionId", "versionName", "status", "createdBy", "createdAt")) {
			assertThat(response.has(field)).as(field).isTrue();
		}
	}

	private ObjectNode minimal() {
		var node = mapper.createObjectNode();
		node.put("versionName", "v2-draft");
		node.set("allowedTools", mapper.readTree("[]"));
		return node;
	}

	private String nestedJson(int containers, boolean object) {
		return object ? "{\"n\":".repeat(containers) + "0" + "}".repeat(containers)
				: "[".repeat(containers) + "0" + "]".repeat(containers);
	}

	private ResultActions create(ObjectNode body, User actor) throws Exception {
		return mvc.perform(post(url()).header(HttpHeaders.AUTHORIZATION, bearer(actor)).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(body)));
	}

	private long createdId(ResultActions result) throws Exception {
		return mapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data").path("versionId").longValue();
	}

	private String url() { return "/api/v1/policies/" + policy.getId() + "/versions"; }
	private String bearer(User actor) { return "Bearer " + tokens.issue(actor.getId()).accessToken(); }
	private User user(Company target, RoleType role) {
		return users.save(User.builder().company(target).employeeNumber(UUID.randomUUID().toString()).passwordHash("test-hash")
				.userName("초안 테스트").roleType(role).failedLoginCount(0).build());
	}
}
