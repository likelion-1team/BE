package com.babylion.team1.domain.policy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.babylion.team1.global.auth.JwtTokens;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 기존 로컬 MySQL 환경을 사용하며 생성한 테스트 데이터는 매 테스트 후 롤백한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PolicyControllerIntegrationTest {
	private static final String URL = "/api/v1/policies";
	private static final LocalDateTime APPLIED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

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
	@Autowired private PolicyVersionRepository versions;
	@Autowired private ProjectRepository projects;
	@Autowired private ProjectPolicyMappingRepository mappings;
	@Autowired private EntityManager entityManager;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper objectMapper;

	private Company company;
	private User admin;
	private User otherAdmin;
	private PolicyVersion active;
	private PolicyVersion rolledBack;
	private PolicyVersion draft;
	private PolicyVersion support;
	private PolicyVersion otherVersion;

	@BeforeEach
	void setUp() {
		String suffix = UUID.randomUUID().toString();
		company = companies.save(Company.builder().companyCode("PL-A-" + suffix).companyName("정책 A").build());
		var otherCompany = companies.save(Company.builder().companyCode("PL-B-" + suffix).companyName("정책 B").build());
		admin = user(company, RoleType.ADMIN);
		otherAdmin = user(otherCompany, RoleType.ADMIN);
		var invoicePolicy = policies.save(Policy.builder().company(company).taskType("INVOICE").createdBy(admin).build());
		var supportPolicy = policies.save(Policy.builder().company(company).taskType("SUPPORT").createdBy(admin).build());
		var otherPolicy = policies.save(Policy.builder().company(otherCompany).taskType("INVOICE").createdBy(otherAdmin).build());
		active = version(invoicePolicy, admin, PolicyVersionStatus.ACTIVE, "v1");
		rolledBack = version(invoicePolicy, admin, PolicyVersionStatus.ROLLED_BACK, "v0");
		draft = version(invoicePolicy, admin, PolicyVersionStatus.DRAFT, "v2-draft");
		support = version(supportPolicy, admin, PolicyVersionStatus.ACTIVE, "support-v1");
		otherVersion = version(otherPolicy, otherAdmin, PolicyVersionStatus.ACTIVE, "other-v1");
		var first = project(company, "A");
		var second = project(company, "B");
		var foreign = project(otherCompany, "FOREIGN");
		map(active, first);
		map(active, first); // 잘못 중복 저장된 매핑도 count는 중복하지 않는다.
		map(active, second);
		map(active, foreign); // 교차 회사 매핑이 있더라도 집계에서 제외한다.
		map(rolledBack, first);
		map(otherVersion, first); // 외부 회사 버전에 대한 집계 조회도 차단해야 한다.
		entityManager.flush();
		for (var version : List.of(active, rolledBack, draft, support, otherVersion)) {
			jdbc.update("update policy_version set created_at = ? where version_id = ?",
					Timestamp.valueOf(LocalDateTime.of(2026, 11, 1, 9, 0)), version.getId());
		}
	}

	@Test
	void defaultsAreVersionBasedAndDoNotRequireUserProjectMappings() throws Exception {
		mvc.perform(authorized(get(URL), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("S200"))
				.andExpect(jsonPath("$.message").value("업무 유형별 정책 목록 조회에 성공했습니다"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.items[*].versionId")
						.value(contains(support.getId().intValue(), rolledBack.getId().intValue(), active.getId().intValue())))
				.andExpect(jsonPath("$.data.items[*].projectCount").value(contains(0, 1, 2)))
				.andExpect(jsonPath("$.data.items[2].appliedAt").value("2026-11-01T10:00:00"));
	}

	@Test
	void taskTypeAndStatusFiltersAreCombinedAndNormalized() throws Exception {
		mvc.perform(authorized(get(URL).param("taskType", " INVOICE ").param("status", "ACTIVE"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].versionId").value(active.getId()))
				.andExpect(jsonPath("$.data.items[0].taskType").value("INVOICE"))
				.andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.items[0].projectCount").value(2));
		mvc.perform(authorized(get(URL).param("taskType", "INVOICE"), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
		mvc.perform(authorized(get(URL).param("status", "ROLLED_BACK"), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	void explicitDraftIncludesUnmappedVersionWithNullAppliedAt() throws Exception {
		mvc.perform(authorized(get(URL).param("status", "DRAFT"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].versionId").value(draft.getId()))
				.andExpect(jsonPath("$.data.items[0].projectCount").value(0))
				.andExpect(jsonPath("$.data.items[0].appliedAt").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void paginationUsesStableVersionOrderAndKeepsTotalOutsideLastPage() throws Exception {
		var ordered = List.of(support, rolledBack, active);
		for (int page = 0; page < ordered.size(); page++) {
			mvc.perform(authorized(get(URL).param("page", Integer.toString(page)).param("size", "1"), admin))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.page").value(page))
					.andExpect(jsonPath("$.data.totalElements").value(3))
					.andExpect(jsonPath("$.data.items.length()").value(1))
					.andExpect(jsonPath("$.data.items[0].versionId").value(ordered.get(page).getId()));
		}
		mvc.perform(authorized(get(URL).param("page", "3").param("size", "1"), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.totalElements").value(3));
	}

	@Test
	void blankFilterIsIgnoredAndUnknownTaskReturnsEmptyCollection() throws Exception {
		mvc.perform(authorized(get(URL).param("taskType", "   ").param("size", "100"), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
		mvc.perform(authorized(get(URL).param("taskType", "MISSING"), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	void companyScopeCannotBeOverriddenAndMappingCountsExcludeForeignVersions() throws Exception {
		mvc.perform(authorized(get(URL).param("companyId", otherAdmin.getCompany().getId().toString()), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
		mvc.perform(authorized(get(URL), otherAdmin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].versionId").value(otherVersion.getId()))
				.andExpect(jsonPath("$.data.items[0].projectCount").value(0));
		assertThat(mappings.countProjectsByVersionIds(List.of(otherVersion.getId()), company.getId())).isEmpty();
	}

	@Test
	void approverIsAllowedButOrdinaryUserIsForbidden() throws Exception {
		mvc.perform(authorized(get(URL), user(company, RoleType.APPROVER)))
				.andExpect(status().isOk());
		mvc.perform(authorized(get(URL), user(company, RoleType.USER)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("POLICY_ACCESS_DENIED"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void bearerAuthenticationRejectsMissingInvalidRefreshAndUnknownUsers() throws Exception {
		mvc.perform(get(URL)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		for (String token : List.of("invalid.token.value", tokens.issue(admin.getId()).refreshToken(),
				tokens.issue(Long.MAX_VALUE).accessToken())) {
			mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	void malformedFiltersAndPageLimitsReturnValidationErrors() throws Exception {
		for (String[] invalid : List.of(new String[]{"status", "APPLIED"},
				new String[]{"page", "-1"}, new String[]{"page", "abc"},
				new String[]{"page", "2147483647"},
				new String[]{"size", "0"}, new String[]{"size", "101"},
				new String[]{"size", "abc"}, new String[]{"taskType", "x".repeat(51)})) {
			mvc.perform(authorized(get(URL).param(invalid[0], invalid[1]), admin))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
					.andExpect(jsonPath("$.errors").isArray());
		}
	}

	@Test
	void historyIncludesAllStatesAuthorAndDistinctCurrentCompanyProjects() throws Exception {
		mvc.perform(authorized(get(historyUrl()), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("정책 버전 이력 목록 조회에 성공했습니다"))
				.andExpect(jsonPath("$.data.policyId").value(active.getPolicy().getId()))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.items[*].versionId")
						.value(contains(draft.getId().intValue(), rolledBack.getId().intValue(), active.getId().intValue())))
				.andExpect(jsonPath("$.data.items[*].status").value(contains("DRAFT", "ROLLED_BACK", "ACTIVE")))
				.andExpect(jsonPath("$.data.items[0].projects").isEmpty())
				.andExpect(jsonPath("$.data.items[0].appliedAt").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.items[2].createdBy.userId").value(admin.getId()))
				.andExpect(jsonPath("$.data.items[2].createdBy.userName").value("정책 테스트"))
				.andExpect(jsonPath("$.data.items[2].createdAt").value("2026-11-01T09:00:00"))
				.andExpect(jsonPath("$.data.items[2].appliedAt").value("2026-11-01T10:00:00"))
				.andExpect(jsonPath("$.data.items[2].projects[*].projectName").value(contains("A", "B")));
		assertThat(mappings.findProjectsByVersionIds(List.of(otherVersion.getId()), company.getId())).isEmpty();
	}

	@Test
	void historyPaginationUsesCreatedAtThenIdAndCountsVersionsNotMappings() throws Exception {
		jdbc.update("update policy_version set created_at = ? where version_id = ?",
				Timestamp.valueOf(LocalDateTime.of(2026, 11, 2, 9, 0)), active.getId());
		var ordered = List.of(active, draft, rolledBack);
		for (int page = 0; page < ordered.size(); page++) {
			mvc.perform(authorized(get(historyUrl()).param("page", Integer.toString(page)).param("size", "1"), admin))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.totalElements").value(3))
					.andExpect(jsonPath("$.data.page").value(page))
					.andExpect(jsonPath("$.data.size").value(1))
					.andExpect(jsonPath("$.data.items.length()").value(1))
					.andExpect(jsonPath("$.data.items[0].versionId").value(ordered.get(page).getId()));
		}
		mvc.perform(authorized(get(historyUrl()).param("page", "3").param("size", "1"), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.totalElements").value(3));
	}

	@Test
	void existingPolicyWithNoVersionsReturnsEmptyHistory() throws Exception {
		var empty = policies.save(Policy.builder().company(company).taskType("EMPTY").createdBy(admin).build());
		mvc.perform(authorized(get(URL + "/" + empty.getId() + "/versions").param("size", "100"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.policyId").value(empty.getId()))
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	void detailReturnsJsonStructuresDecimalAndUserIdsWithoutChangingStoredVersion() throws Exception {
		jdbc.update("""
				update policy_version set confidence_threshold = ?, allowed_tools = ?,
				quality_time_criteria = ?, exception_conditions = ?, success_definition = ?,
				default_route = ?, approved_by = ? where version_id = ?
				""", new java.math.BigDecimal("85.25"), "[\"AI\",\"ERP\"]",
				"{\"quality\":{\"minimum\":90},\"seconds\":30}", "[{\"route\":\"MANUAL_REVIEW\"}]",
				"검토 통과", "AI_ERP_DIRECT", admin.getId(), active.getId());
		entityManager.clear();
		long mappingCount = mappings.count();
		long versionCount = versions.count();
		mvc.perform(authorized(get(detailUrl(active)), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("특정 정책 버전 상세 조회에 성공했습니다"))
				.andExpect(jsonPath("$.data.policyId").value(active.getPolicy().getId()))
				.andExpect(jsonPath("$.data.taskType").value("INVOICE"))
				.andExpect(jsonPath("$.data.versionId").value(active.getId()))
				.andExpect(jsonPath("$.data.versionName").value("v1"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.confidenceThreshold").value(85.25))
				.andExpect(jsonPath("$.data.allowedTools").value(contains("AI", "ERP")))
				.andExpect(jsonPath("$.data.qualityTimeCriteria.quality.minimum").value(90))
				.andExpect(jsonPath("$.data.exceptionConditions[0].route").value("MANUAL_REVIEW"))
				.andExpect(jsonPath("$.data.successDefinition").value("검토 통과"))
				.andExpect(jsonPath("$.data.defaultRoute").value("AI_ERP_DIRECT"))
				.andExpect(jsonPath("$.data.createdBy").value(admin.getId()))
				.andExpect(jsonPath("$.data.approvedBy").value(admin.getId()))
				.andExpect(jsonPath("$.data.createdAt").value("2026-11-01T09:00:00"))
				.andExpect(jsonPath("$.data.appliedAt").value("2026-11-01T10:00:00"));
		assertThat(mappings.count()).isEqualTo(mappingCount);
		assertThat(versions.count()).isEqualTo(versionCount);
		assertThat(versions.findById(active.getId()).orElseThrow().getStatus()).isEqualTo(PolicyVersionStatus.ACTIVE);
	}

	@Test
	void draftDetailPreservesSqlNullAndEmptyJsonWithoutDroppingUnapprovedVersion() throws Exception {
		jdbc.update("update policy_version set quality_time_criteria = ?, exception_conditions = ? where version_id = ?",
				"{}", "[]", draft.getId());
		entityManager.clear();
		mvc.perform(authorized(get(detailUrl(draft)), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DRAFT"))
				.andExpect(jsonPath("$.data.allowedTools").isArray())
				.andExpect(jsonPath("$.data.allowedTools").isEmpty())
				.andExpect(jsonPath("$.data.qualityTimeCriteria").isMap())
				.andExpect(jsonPath("$.data.qualityTimeCriteria").isEmpty())
				.andExpect(jsonPath("$.data.exceptionConditions").isArray())
				.andExpect(jsonPath("$.data.exceptionConditions").isEmpty())
				.andExpect(jsonPath("$.data.confidenceThreshold").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.defaultRoute").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.successDefinition").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.approvedBy").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.appliedAt").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.rolledBackAt").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.rollbackReason").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void detailPreservesJsonNullAndSqlNull() throws Exception {
		jdbc.update("update policy_version set allowed_tools = ?, quality_time_criteria = ? where version_id = ?",
				"null", "null", draft.getId());
		entityManager.clear();
		mvc.perform(authorized(get(detailUrl(draft)), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.allowedTools").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.qualityTimeCriteria").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.exceptionConditions").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void rolledBackDetailReturnsRollbackMetadata() throws Exception {
		jdbc.update("update policy_version set rolled_back_at = ?, rollback_reason = ? where version_id = ?",
				Timestamp.valueOf(LocalDateTime.of(2026, 11, 3, 12, 0)), "품질 지표 저하", rolledBack.getId());
		entityManager.clear();
		mvc.perform(authorized(get(detailUrl(rolledBack)), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ROLLED_BACK"))
				.andExpect(jsonPath("$.data.rolledBackAt").value("2026-11-03T12:00:00"))
				.andExpect(jsonPath("$.data.rollbackReason").value("품질 지표 저하"));
	}

	@Test
	void foreignAndMissingPoliciesHaveSameNotFoundResponseOnBothEndpoints() throws Exception {
		for (long policyId : List.of(otherVersion.getPolicy().getId(), Long.MAX_VALUE)) {
			String history = URL + "/" + policyId + "/versions";
			for (String path : List.of(history, history + "/" + otherVersion.getId())) {
				mvc.perform(authorized(get(path), admin))
						.andExpect(status().isNotFound())
						.andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"))
						.andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
			}
		}
		assertThat(versions.findVersionHistory(otherVersion.getPolicy().getId(), company.getId(),
				org.springframework.data.domain.PageRequest.of(0, 20))).isEmpty();
	}

	@Test
	void detailRejectsWrongPolicyVersionPairsEvenWithinSameCompany() throws Exception {
		for (long versionId : List.of(support.getId(), otherVersion.getId(), Long.MAX_VALUE)) {
			mvc.perform(authorized(get(historyUrl() + "/" + versionId), admin))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("POLICY_VERSION_NOT_FOUND"));
		}
		assertThat(versions.findVersionDetail(active.getPolicy().getId(), otherVersion.getId(), company.getId())).isEmpty();
		assertThat(versions.findVersionDetail(otherVersion.getPolicy().getId(), otherVersion.getId(), company.getId())).isEmpty();
	}

	@Test
	void versionEndpointsAllowApproverWithoutProjectMembershipButRejectUser() throws Exception {
		var approver = user(company, RoleType.APPROVER);
		var ordinary = user(company, RoleType.USER);
		for (String path : List.of(historyUrl(), detailUrl(active))) {
			mvc.perform(authorized(get(path), approver)).andExpect(status().isOk());
			mvc.perform(authorized(get(path), ordinary))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("POLICY_ACCESS_DENIED"));
		}
	}

	@Test
	void versionEndpointsUseExistingBearerAuthentication() throws Exception {
		for (String path : List.of(historyUrl(), detailUrl(active))) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized());
			for (String token : List.of("invalid.token.value", tokens.issue(admin.getId()).refreshToken(),
					tokens.issue(Long.MAX_VALUE).accessToken())) {
				mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
						.andExpect(status().isUnauthorized())
						.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
			}
		}
	}

	@Test
	void historyRejectsInvalidPageAndOffsetValues() throws Exception {
		for (String[] invalid : List.of(new String[]{"page", "-1"}, new String[]{"page", "abc"},
				new String[]{"page", "2147483647"}, new String[]{"size", "0"},
				new String[]{"size", "101"}, new String[]{"size", "abc"})) {
			mvc.perform(authorized(get(historyUrl()).param(invalid[0], invalid[1]), admin))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
					.andExpect(jsonPath("$.errors").isArray());
		}
	}

	@Test
	void versionEndpointsRejectNonpositiveAndMalformedPathIds() throws Exception {
		for (String id : List.of("0", "-1")) {
			for (String path : List.of(URL + "/" + id + "/versions", URL + "/" + id + "/versions/1", historyUrl() + "/" + id)) {
				mvc.perform(authorized(get(path), admin)).andExpect(status().isBadRequest())
						.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
			}
		}
		for (String id : List.of("abc", "9223372036854775808")) {
			for (String path : List.of(URL + "/" + id + "/versions", URL + "/" + id + "/versions/1", historyUrl() + "/" + id)) {
				mvc.perform(authorized(get(path), admin)).andExpect(status().isBadRequest())
						.andExpect(jsonPath("$.code").value("INVALID_TYPE"));
			}
		}
	}

	@Test
	void swaggerDocumentsBothVersionPathsParametersAndBearerScheme() throws Exception {
		var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		var doc = objectMapper.readTree(result.getResponse().getContentAsString());
		var history = doc.path("paths").path(URL + "/{policyId}/versions").path("get");
		var detail = doc.path("paths").path(URL + "/{policyId}/versions/{versionId}").path("get");
		assertThat(history.isMissingNode()).isFalse();
		assertThat(detail.isMissingNode()).isFalse();
		var historyParameters = new ArrayList<String>();
		for (var parameter : history.path("parameters")) {
			historyParameters.add(parameter.path("name").asString());
		}
		var detailParameters = new ArrayList<String>();
		for (var parameter : detail.path("parameters")) {
			detailParameters.add(parameter.path("name").asString());
		}
		assertThat(historyParameters).containsExactlyInAnyOrder("policyId", "page", "size");
		assertThat(detailParameters).containsExactlyInAnyOrder("policyId", "versionId");
		assertThat(history.path("security").get(0).has("bearerAuth")).isTrue();
		assertThat(detail.path("security").get(0).has("bearerAuth")).isTrue();
		assertThat(doc.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asString()).isEqualTo("bearer");
	}

	@Test
	void swaggerKeepsPolicyListAndHistoryItemSchemasSeparate() throws Exception {
		var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		var schemas = objectMapper.readTree(result.getResponse().getContentAsString()).path("components").path("schemas");
		String listItemRef = schemas.path("PolicyListResponse").path("properties").path("items").path("items").path("$ref").asString();
		String historyItemRef = schemas.path("PolicyVersionHistoryResponse").path("properties").path("items").path("items").path("$ref").asString();
		assertThat(historyItemRef).isNotEqualTo(listItemRef);
		var listItem = schemas.path(listItemRef.substring(listItemRef.lastIndexOf('/') + 1)).path("properties");
		var historyItem = schemas.path(historyItemRef.substring(historyItemRef.lastIndexOf('/') + 1)).path("properties");
		assertThat(listItem.has("projectCount")).isTrue();
		assertThat(listItem.has("projects")).isFalse();
		assertThat(historyItem.has("createdAt")).isTrue();
		assertThat(historyItem.has("createdBy")).isTrue();
		assertThat(historyItem.has("projects")).isTrue();
		assertThat(historyItem.has("projectCount")).isFalse();
		String authorRef = historyItem.path("createdBy").path("$ref").asString();
		String projectRef = historyItem.path("projects").path("items").path("$ref").asString();
		var author = schemas.path(authorRef.substring(authorRef.lastIndexOf('/') + 1)).path("properties");
		var project = schemas.path(projectRef.substring(projectRef.lastIndexOf('/') + 1)).path("properties");
		assertThat(author.has("userId") && author.has("userName")).isTrue();
		assertThat(project.has("projectId") && project.has("projectName")).isTrue();
	}

	@Test
	void swaggerJsonSettingsSupportStoredJsonTypesIncludingArraysAndNull() throws Exception {
		var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		var properties = objectMapper.readTree(result.getResponse().getContentAsString())
				.path("components").path("schemas").path("PolicyVersionDetailResponse").path("properties");
		for (String field : List.of("allowedTools", "qualityTimeCriteria", "exceptionConditions")) {
			var types = new ArrayList<String>();
			var type = properties.path(field).path("type");
			if (type.isArray()) {
				for (var value : type) {
					types.add(value.asString());
				}
			} else {
				types.add(type.asString());
			}
			assertThat(types).as("%s의 JSON 응답 스키마", field)
					.containsExactlyInAnyOrder("object", "array", "string", "number", "boolean", "null");
		}
	}

	@Test
	void diffReturnsAllSettingsInFixedOrderWithoutChangingDatabase() throws Exception {
		jdbc.update("""
				update policy_version set confidence_threshold = ?, allowed_tools = ?, success_definition = ?,
				quality_time_criteria = ?, exception_conditions = ?, default_route = ? where version_id = ?
				""", new java.math.BigDecimal("85.25"), "[\"AI\",\"ERP\"]", "검토 통과", "{}", "[]", "LLM", draft.getId());
		entityManager.clear();
		var before = jdbc.queryForList("select * from policy_version where policy_id = ? order by version_id", active.getPolicy().getId());
		long mappingCount = mappings.count();
		Long auditCount = jdbc.queryForObject("select count(*) from audit_log", Long.class);
		mvc.perform(authorized(diffRequest(active.getId(), draft.getId()), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("S200"))
				.andExpect(jsonPath("$.message").value("정책 버전 Diff 비교에 성공했습니다"))
				.andExpect(jsonPath("$.data.policyId").value(active.getPolicy().getId()))
				.andExpect(jsonPath("$.data.baseId").value(active.getId()))
				.andExpect(jsonPath("$.data.targetId").value(draft.getId()))
				.andExpect(jsonPath("$.data.changes[*].field").value(contains("confidenceThreshold", "allowedTools",
						"successDefinition", "qualityTimeCriteria", "exceptionConditions", "defaultRoute")))
				.andExpect(jsonPath("$.data.changes[0].baseValue").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.changes[0].targetValue").value(85.25))
				.andExpect(jsonPath("$.data.changes[1].baseValue").isArray())
				.andExpect(jsonPath("$.data.changes[1].targetValue").value(contains("AI", "ERP")))
				.andExpect(jsonPath("$.data.changes[2].targetValue").value("검토 통과"))
				.andExpect(jsonPath("$.data.changes[3].targetValue").isMap())
				.andExpect(jsonPath("$.data.changes[4].targetValue").isArray())
				.andExpect(jsonPath("$.data.changes[5].targetValue").value("LLM"));
		assertThat(jdbc.queryForList("select * from policy_version where policy_id = ? order by version_id", active.getPolicy().getId()))
				.isEqualTo(before);
		assertThat(mappings.count()).isEqualTo(mappingCount);
		assertThat(jdbc.queryForObject("select count(*) from audit_log", Long.class)).isEqualTo(auditCount);
	}

	@Test
	void diffTreatsEquivalentJsonAndMetadataAsUnchangedIncludingSameVersion() throws Exception {
		jdbc.update("update policy_version set confidence_threshold = ?, quality_time_criteria = ? where version_id = ?",
				new java.math.BigDecimal("80.00"), "{\"a\":[1,{\"n\":2.0}],\"enabled\":true}", active.getId());
		jdbc.update("update policy_version set confidence_threshold = ?, quality_time_criteria = ? where version_id = ?",
				new java.math.BigDecimal("80.00"), "{\"enabled\":true,\"a\":[1.0,{\"n\":2}]}", draft.getId());
		entityManager.clear();
		mvc.perform(authorized(diffRequest(active.getId(), draft.getId()), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.changes").isEmpty());
		mvc.perform(authorized(diffRequest(active.getId(), active.getId()), admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.changes").isEmpty());
		mvc.perform(authorized(get(detailUrl(active)), admin)).andExpect(status().isOk());
	}

	@Test
	void diffDistinguishesArrayOrderNullAndEmptyStructures() throws Exception {
		jdbc.update("update policy_version set allowed_tools = ?, exception_conditions = ? where version_id = ?",
				"[\"AI\",\"ERP\"]", "null", active.getId());
		jdbc.update("update policy_version set allowed_tools = ?, quality_time_criteria = ?, exception_conditions = ? where version_id = ?",
				"[\"ERP\",\"AI\"]", "{}", "[]", draft.getId());
		entityManager.clear();
		mvc.perform(authorized(diffRequest(active.getId(), draft.getId()), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.changes[*].field").value(contains("allowedTools", "qualityTimeCriteria", "exceptionConditions")))
				.andExpect(jsonPath("$.data.changes[0].baseValue").value(contains("AI", "ERP")))
				.andExpect(jsonPath("$.data.changes[0].targetValue").value(contains("ERP", "AI")))
				.andExpect(jsonPath("$.data.changes[1].baseValue").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.changes[1].targetValue").isMap())
				.andExpect(jsonPath("$.data.changes[2].baseValue").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.changes[2].targetValue").isArray());
	}

	@Test
	void diffChecksPolicyCompanyBeforeVersions() throws Exception {
		for (long policyId : List.of(otherVersion.getPolicy().getId(), Long.MAX_VALUE)) {
			mvc.perform(authorized(get(URL + "/" + policyId + "/versions/diff")
					.param("baseId", otherVersion.getId().toString()).param("targetId", otherVersion.getId().toString()), admin))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
		}
	}

	@Test
	void diffRejectsWrongPolicyAndForeignVersionInEitherPosition() throws Exception {
		for (long versionId : List.of(support.getId(), otherVersion.getId(), Long.MAX_VALUE)) {
			mvc.perform(authorized(diffRequest(versionId, active.getId()), admin))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_VERSION_NOT_FOUND"));
			mvc.perform(authorized(diffRequest(active.getId(), versionId), admin))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_VERSION_NOT_FOUND"));
		}
		mvc.perform(authorized(diffRequest(Long.MAX_VALUE, Long.MAX_VALUE), admin))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_VERSION_NOT_FOUND"));
	}

	@Test
	void diffUsesExistingRolesAndBearerAuthentication() throws Exception {
		mvc.perform(authorized(diffRequest(active.getId(), draft.getId()), user(company, RoleType.APPROVER)))
				.andExpect(status().isOk());
		mvc.perform(authorized(diffRequest(active.getId(), draft.getId()), user(company, RoleType.USER)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("POLICY_ACCESS_DENIED"));
		mvc.perform(diffRequest(active.getId(), draft.getId())).andExpect(status().isUnauthorized());
		for (String token : List.of("invalid.token.value", tokens.issue(admin.getId()).refreshToken(),
				tokens.issue(Long.MAX_VALUE).accessToken())) {
			mvc.perform(diffRequest(active.getId(), draft.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		}
	}

	@Test
	void diffRejectsMissingInvalidAndNonpositiveInputs() throws Exception {
		for (var request : List.of(get(historyUrl() + "/diff"),
				get(historyUrl() + "/diff").param("baseId", active.getId().toString()),
				get(historyUrl() + "/diff").param("targetId", draft.getId().toString()),
				get(historyUrl() + "/diff").param("baseId", "").param("targetId", draft.getId().toString()))) {
			mvc.perform(authorized(request, admin)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
		}
		for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
			String code = id.equals("0") || id.equals("-1") ? "INVALID_REQUEST" : "INVALID_TYPE";
			var badPolicy = get(URL + "/" + id + "/versions/diff")
					.param("baseId", active.getId().toString()).param("targetId", draft.getId().toString());
			var badBase = get(historyUrl() + "/diff").param("baseId", id).param("targetId", draft.getId().toString());
			var badTarget = get(historyUrl() + "/diff").param("baseId", active.getId().toString()).param("targetId", id);
			for (var request : List.of(badPolicy, badBase, badTarget)) {
				mvc.perform(authorized(request, admin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
			}
		}
	}

	@Test
	void diffReturnsOneWholeFieldForNestedChangeAndPreservesJsonValues() throws Exception {
		jdbc.update("update policy_version set quality_time_criteria = ? where version_id = ?",
				"{\"nested\":{\"limit\":10},\"keep\":true}", active.getId());
		jdbc.update("update policy_version set quality_time_criteria = ? where version_id = ?",
				"{\"nested\":{\"limit\":11},\"keep\":true}", draft.getId());
		entityManager.clear();
		mvc.perform(authorized(diffRequest(active.getId(), draft.getId()), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.changes.length()").value(1))
				.andExpect(jsonPath("$.data.changes[0].field").value("qualityTimeCriteria"))
				.andExpect(jsonPath("$.data.changes[0].baseValue.nested.limit").value(10))
				.andExpect(jsonPath("$.data.changes[0].targetValue.nested.limit").value(11))
				.andExpect(jsonPath("$.data.changes[0].targetValue.keep").value(true));
	}

	@Test
	void swaggerDocumentsDiffStaticRouteAndJsonChangeSchema() throws Exception {
		var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		var doc = objectMapper.readTree(result.getResponse().getContentAsString());
		var operation = doc.path("paths").path(URL + "/{policyId}/versions/diff").path("get");
		assertThat(operation.isMissingNode()).isFalse();
		var parameters = new ArrayList<String>();
		for (var parameter : operation.path("parameters")) {
			parameters.add(parameter.path("name").asString());
			assertThat(parameter.path("required").asBoolean()).isTrue();
		}
		assertThat(parameters).containsExactlyInAnyOrder("policyId", "baseId", "targetId");
		assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
		var schemas = doc.path("components").path("schemas");
		assertThat(schemas.path("PolicyVersionDiffResponse").path("properties").path("changes").path("items").path("$ref").asString())
				.isEqualTo("#/components/schemas/PolicyVersionDiffChange");
		var properties = schemas.path("PolicyVersionDiffChange").path("properties");
		assertThat(properties.has("field")).isTrue();
		for (String field : List.of("baseValue", "targetValue")) {
			var types = new ArrayList<String>();
			for (var type : properties.path(field).path("type")) {
				types.add(type.asString());
			}
			assertThat(types).containsExactlyInAnyOrder("object", "array", "string", "number", "boolean", "null");
		}
	}

	private MockHttpServletRequestBuilder diffRequest(long baseId, long targetId) {
		return get(historyUrl() + "/diff").param("baseId", Long.toString(baseId)).param("targetId", Long.toString(targetId));
	}

	private String historyUrl() {
		return URL + "/" + active.getPolicy().getId() + "/versions";
	}

	private String detailUrl(PolicyVersion version) {
		return URL + "/" + version.getPolicy().getId() + "/versions/" + version.getId();
	}

	private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, User user) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(user.getId()).accessToken());
	}

	private User user(Company target, RoleType role) {
		return users.save(User.builder().company(target).employeeNumber(UUID.randomUUID().toString())
				.passwordHash("test-hash").userName("정책 테스트").roleType(role).failedLoginCount(0).build());
	}

	private PolicyVersion version(Policy policy, User author, PolicyVersionStatus status, String name) {
		return versions.save(PolicyVersion.builder().policy(policy).createdBy(author).versionName(name)
				.status(status).allowedTools("[]").appliedAt(status == PolicyVersionStatus.DRAFT ? null : APPLIED_AT).build());
	}

	private Project project(Company target, String code) {
		return projects.save(Project.builder().company(target).projectCode(code).projectName(code).build());
	}

	private void map(PolicyVersion version, Project project) {
		mappings.save(ProjectPolicyMapping.builder().policyVersion(version).project(project).build());
	}
}
