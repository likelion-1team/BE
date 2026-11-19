package com.babylion.team1.domain.policy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.babylion.team1.domain.audit.entity.AuditLog;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.policy.entity.Policy;
import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.policy.entity.ProjectPolicyMapping;
import com.babylion.team1.domain.policy.repository.PolicyRepository;
import com.babylion.team1.domain.policy.service.PolicyApplyService;
import com.babylion.team1.domain.project.entity.Project;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyApplyAtomicityIntegrationTest {
	@DynamicPropertySource static void jwt(DynamicPropertyRegistry r) {
		byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
		String secret = java.util.Base64.getEncoder().encodeToString(bytes); r.add("jwt.secret", () -> secret);
	}
	@Autowired EntityManager em;
	@Autowired PlatformTransactionManager transactions;
	@Autowired JdbcTemplate jdbc;
	@Autowired PolicyApplyService service;
	@MockitoSpyBean PolicyRepository policies;
	@MockitoSpyBean AuditLogRepository audits;
	Fixture f;
	@BeforeEach void setup() {
		f = new TransactionTemplate(transactions).execute(tx -> {
			var c = Company.builder().companyCode("AP-RACE-" + UUID.randomUUID()).companyName("race").build(); em.persist(c);
			var u = User.builder().company(c).employeeNumber(UUID.randomUUID().toString()).userName("race").passwordHash("test").roleType(RoleType.APPROVER).failedLoginCount(0).build(); em.persist(u);
			var p = Policy.builder().company(c).createdBy(u).taskType("INVOICE").build(); em.persist(p);
			var project = Project.builder().company(c).projectCode(UUID.randomUUID().toString()).projectName("race").build(); em.persist(project);
			var unmapped = Project.builder().company(c).projectCode(UUID.randomUUID().toString()).projectName("unmapped").build();
			em.persist(unmapped);
			var old = version(p, u, PolicyVersionStatus.ACTIVE); var a = version(p, u, PolicyVersionStatus.DRAFT); var b = version(p, u, PolicyVersionStatus.DRAFT);
			em.persist(ProjectPolicyMapping.builder().project(project).policyVersion(old).build()); em.flush();
			return new Fixture(c.getId(), u.getId(), p.getId(), project.getId(), unmapped.getId(), old.getId(), a.getId(), b.getId());
		});
	}
	@AfterEach void cleanup() {
		reset(policies, audits);
		if (f != null) new TransactionTemplate(transactions).executeWithoutResult(tx -> {
			jdbc.update("delete from audit_log where user_id=?", f.user());
			jdbc.update("delete from project_policy_mapping where project_id in (?,?)", f.project(), f.unmapped());
			jdbc.update("delete from policy_version where policy_id=?", f.policy());
			jdbc.update("delete from project where project_id in (?,?)", f.project(), f.unmapped());
			jdbc.update("delete from policy where policy_id=?", f.policy());
			jdbc.update("delete from users where user_id=?", f.user());
			jdbc.update("delete from company where company_id=?", f.company());
		});
	}
	@Test void simultaneousDifferentVersionsLeaveExactlyOneCurrentMapping() throws Exception {
		barrierBeforeLock();
		assertThat(race(f.a(), f.b())).containsExactlyInAnyOrder(200, 200);
		assertThat(current()).hasSize(1).containsAnyOf(f.a(), f.b());
		assertThat(jdbc.queryForObject("select count(*) from audit_log where user_id=? and event_type='POLICY_APPROVED'", Long.class, f.user())).isEqualTo(2L);
	}
	@Test void simultaneousSameVersionHasOneWinner() throws Exception {
		barrierBeforeLock();
		assertThat(race(f.a(), f.a())).containsExactlyInAnyOrder(200, 409);
		assertThat(current()).containsExactly(f.a());
	}
	@Test
	void auditFailureRollsBackFlushedMappingChangesAndVersion() {
		var failure = new IllegalStateException("audit unavailable");
		doAnswer(invocation -> {
			em.flush();
			assertThat(current()).containsExactly(f.a());
			assertThat(versionsMappedTo(f.unmapped())).containsExactly(f.a());
			var applied = appliedVersionState();
			assertThat(applied).containsEntry("status", "ACTIVE").containsEntry("approved_by", f.user());
			assertThat(applied.get("applied_at")).isNotNull();
			throw failure;
		}).when(audits).save(any(AuditLog.class));

		assertThatThrownBy(() -> service.apply(f.user(), f.policy(), f.a(), List.of(f.project(), f.unmapped())))
				.isSameAs(failure);
		assertThat(current()).containsExactly(f.old());
		assertThat(versionsMappedTo(f.unmapped())).isEmpty();
		assertThat(appliedVersionState()).containsEntry("status", "DRAFT")
				.containsEntry("approved_by", null).containsEntry("applied_at", null);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where user_id=?", Long.class, f.user())).isZero();
	}
	private void barrierBeforeLock() {
		var delegate = new JpaRepositoryFactory(em).getRepository(PolicyRepository.class);
		var barrier = new CyclicBarrier(2);
		doAnswer(inv -> { barrier.await(10, TimeUnit.SECONDS); return delegate.lockForApply(inv.getArgument(0), inv.getArgument(1)); })
				.when(policies).lockForApply(f.policy(), f.company());
	}
	private List<Integer> race(Long first, Long second) throws Exception {
		var pool = Executors.newFixedThreadPool(2);
		try {
			var a = pool.submit(() -> run(first)); var b = pool.submit(() -> run(second));
			return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
		} finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
	}
	private int run(Long version) {
		try { service.apply(f.user(), f.policy(), version, List.of(f.project())); return 200; }
		catch (CustomException e) { assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_APPLY_CONFLICT); return 409; }
	}
	private List<Long> current() {
		return versionsMappedTo(f.project());
	}
	private List<Long> versionsMappedTo(Long projectId) {
		return jdbc.queryForList("select version_id from project_policy_mapping where project_id=?", Long.class, projectId);
	}
	private java.util.Map<String, Object> appliedVersionState() {
		return jdbc.queryForMap("select status, approved_by, applied_at from policy_version where version_id=?", f.a());
	}
	private PolicyVersion version(Policy p, User u, PolicyVersionStatus status) {
		var v = PolicyVersion.builder().policy(p).createdBy(u).versionName(UUID.randomUUID().toString()).status(status).allowedTools("[]").build(); em.persist(v); return v;
	}
	record Fixture(Long company, Long user, Long policy, Long project, Long unmapped, Long old, Long a, Long b) {}
}
