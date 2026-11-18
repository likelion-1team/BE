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
