package com.babylion.team1.domain.policy.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.babylion.team1.global.auth.JwtTokens;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
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

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyApplyIntegrationTest {
	@DynamicPropertySource static void jwt(DynamicPropertyRegistry r) {
		byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
		String secret = Base64.getEncoder().encodeToString(bytes); r.add("jwt.secret", () -> secret);
	}
	@Autowired MockMvc mvc;
	@Autowired JwtTokens tokens;
	@Autowired ObjectMapper mapper;
	@Autowired CompanyRepository companies;
	@Autowired UserRepository users;
	@Autowired PolicyRepository policies;
	@Autowired PolicyVersionRepository versions;
	@Autowired ProjectRepository projects;
	@Autowired ProjectPolicyMappingRepository mappings;
	@Autowired EntityManager em;
	@Autowired JdbcTemplate jdbc;
	Company company, other;
	User approver, admin, ordinary;
	Policy policy, anotherPolicy;
	PolicyVersion draft, old, unrelated;
	Project first, second, untouched, foreign;

	@BeforeEach void setup() {
		company = company(); other = company(); approver = user(company, RoleType.APPROVER);
		admin = user(company, RoleType.ADMIN); ordinary = user(company, RoleType.USER);
		policy = policies.save(Policy.builder().company(company).createdBy(approver).taskType("INVOICE").build());
		anotherPolicy = policies.save(Policy.builder().company(company).createdBy(approver).taskType("SUPPORT").build());
		old = version(policy, PolicyVersionStatus.ACTIVE); draft = version(policy, PolicyVersionStatus.DRAFT);
		unrelated = version(anotherPolicy, PolicyVersionStatus.ACTIVE);
		first = project(company); second = project(company); untouched = project(company); foreign = project(other);
		mapping(first, old); mapping(untouched, old); mapping(first, unrelated);
		em.flush(); em.clear();
	}
	@Test void previewDoesNotWriteAndApplyReplacesOnlySelectedPolicyMappings() throws Exception {
		preview(approver, first.getId(), second.getId(), first.getId()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.projects.length()").value(2))
				.andExpect(jsonPath("$.data.projects[0].currentVersionId").value(old.getId()))
				.andExpect(jsonPath("$.data.projects[1].currentVersionId").doesNotExist());
		assertThat(current(first, policy)).containsExactly(old.getId());
		apply(approver, List.of(first.getId(), second.getId(), first.getId())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.approvedBy").value(approver.getId()))
				.andExpect(jsonPath("$.data.projectIds.length()").value(2));
		em.flush(); em.clear();
		assertThat(current(first, policy)).containsExactly(draft.getId());
		assertThat(current(second, policy)).containsExactly(draft.getId());
		assertThat(current(untouched, policy)).containsExactly(old.getId());
		assertThat(current(first, anotherPolicy)).containsExactly(unrelated.getId());
		assertThat(jdbc.queryForObject("select count(*) from audit_log where user_id=? and event_type='POLICY_APPROVED'", Long.class, approver.getId())).isEqualTo(1L);
		apply(approver, List.of(first.getId())).andExpect(status().isConflict());
	}
	@Test void projectValidationIsAtomicAndHidesOtherCompanies() throws Exception {
		apply(approver, List.of(first.getId(), foreign.getId())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("POLICY_APPLY_PROJECT_NOT_FOUND"));
		apply(approver, List.of(first.getId(), Long.MAX_VALUE)).andExpect(status().isNotFound());
		assertThat(current(first, policy)).containsExactly(old.getId());
		assertThat(versions.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo(PolicyVersionStatus.DRAFT);
	}
	@Test void permissionsAndInputAreEnforced() throws Exception {
		preview(admin, first.getId()).andExpect(status().isOk());
		preview(ordinary, first.getId()).andExpect(status().isForbidden());
		apply(admin, List.of(first.getId())).andExpect(status().isForbidden());
		apply(ordinary, List.of(first.getId())).andExpect(status().isForbidden());
		apply(approver, List.of()).andExpect(status().isBadRequest());
		apply(approver, List.of(0L)).andExpect(status().isBadRequest());
		assertThat(current(first, policy)).containsExactly(old.getId());
	}

	@Test
	void nonIntegerProjectIdsAreRejectedBeforeAnyChanges() throws Exception {
		String projectId = first.getId().toString();
		List<String> invalidIds = List.of(projectId + ".9", projectId + ".0", projectId + "e0",
				"\"" + projectId + "\"", "true", "9223372036854775808");
		for (String invalidId : invalidIds) {
			String body = "{\"projectIds\":[" + second.getId() + "," + invalidId + "]}";
			applyRaw(approver, body).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_JSON"));
		}
		em.flush();
		assertThat(current(first, policy)).containsExactly(old.getId());
		assertThat(current(second, policy)).isEmpty();
		var draftState = jdbc.queryForMap("select status, approved_by, applied_at from policy_version where version_id=?", draft.getId());
		assertThat(draftState).containsEntry("status", "DRAFT")
				.containsEntry("approved_by", null).containsEntry("applied_at", null);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where user_id=?", Long.class, approver.getId())).isZero();
	}

	@Test void existingDuplicateMappingsAreRejectedRatherThanSilentlyDiscarded() throws Exception {
		mapping(first, old); em.flush(); em.clear();
		preview(approver, first.getId()).andExpect(status().isConflict());
		apply(approver, List.of(first.getId())).andExpect(status().isConflict());
		assertThat(current(first, policy)).containsExactlyInAnyOrder(old.getId(), old.getId());
	}
	private ResultActions preview(User u, Long... ids) throws Exception {
		return mvc.perform(get(url() + "/apply-preview").header(HttpHeaders.AUTHORIZATION, bearer(u))
				.param("projectIds", java.util.Arrays.stream(ids).map(String::valueOf).toArray(String[]::new)));
	}
	private ResultActions apply(User u, List<Long> ids) throws Exception {
		return applyRaw(u, mapper.writeValueAsString(java.util.Map.of("projectIds", ids)));
	}
	private ResultActions applyRaw(User u, String body) throws Exception {
		return mvc.perform(post(url() + "/apply").header(HttpHeaders.AUTHORIZATION, bearer(u))
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}
	private String url() { return "/api/v1/policies/" + policy.getId() + "/versions/" + draft.getId(); }
	private String bearer(User u) { return "Bearer " + tokens.issue(u.getId()).accessToken(); }
	private Company company() { return companies.save(Company.builder().companyCode("AP-" + UUID.randomUUID()).companyName("apply").build()); }
	private User user(Company c, RoleType role) { return users.save(User.builder().company(c).employeeNumber(UUID.randomUUID().toString()).userName("apply").passwordHash("test").roleType(role).failedLoginCount(0).build()); }
	private Project project(Company c) { return projects.save(Project.builder().company(c).projectCode(UUID.randomUUID().toString()).projectName("project").build()); }
	private PolicyVersion version(Policy p, PolicyVersionStatus status) { return versions.save(PolicyVersion.builder().policy(p).createdBy(approver).versionName(UUID.randomUUID().toString()).status(status).allowedTools("[]").build()); }
	private void mapping(Project p, PolicyVersion v) { mappings.save(ProjectPolicyMapping.builder().project(p).policyVersion(v).build()); }
	private List<Long> current(Project p, Policy policy) { return jdbc.queryForList("select m.version_id from project_policy_mapping m join policy_version v on v.version_id=m.version_id where m.project_id=? and v.policy_id=?", Long.class, p.getId(), policy.getId()); }
}
