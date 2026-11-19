package com.babylion.team1.domain.policy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.audit.entity.AuditEventType;
import com.babylion.team1.domain.audit.entity.AuditLog;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.dashboard.dto.request.DecisionRecordRequest;
import com.babylion.team1.domain.dashboard.service.DashboardDecisionService;
import com.babylion.team1.domain.policy.dto.request.PolicyRecommendationApproveRequest;
import com.babylion.team1.domain.policy.dto.request.PolicyRecommendationRejectRequest;
import com.babylion.team1.domain.policy.entity.DecisionResult;
import com.babylion.team1.domain.policy.entity.Policy;
import com.babylion.team1.domain.policy.entity.PolicyRecommendation;
import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import com.babylion.team1.domain.policy.repository.PolicyRecommendationRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.policy.service.PolicyRecommendationCommandService;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.auth.JwtTokens;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 테스트 트랜잭션을 쓰지 않고, 서로 다른 실제 트랜잭션의 커밋·롤백 결과를 조회한다. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyRecommendationDecisionAtomicityIntegrationTest {
	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) {
		byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
		String secret = Base64.getEncoder().encodeToString(key);
		registry.add("jwt.secret", () -> secret);
	}
	@Autowired PolicyRecommendationCommandService commands;
	@Autowired DashboardDecisionService dashboard;
	@Autowired EntityManager em;
	@Autowired PlatformTransactionManager transactions;
	@Autowired JdbcTemplate jdbc;
	@Autowired MockMvc mvc;
	@Autowired JwtTokens tokens;
	@Autowired ObjectMapper mapper;
	@MockitoSpyBean PolicyRecommendationRepository recommendations;
	@MockitoSpyBean PolicyVersionRepository versions;
	@MockitoSpyBean AuditLogRepository audits;
	private Fixture fixture;
	private PolicyRecommendationRepository readDelegate;

	@BeforeEach
	void createCommittedFixture() {
		// @Query 메서드는 실제 Java 구현이 없으므로 동일 인터페이스의 실제 JPA 프록시에 위임한다.
		readDelegate = new JpaRepositoryFactory(em).getRepository(PolicyRecommendationRepository.class);
		fixture = new TransactionTemplate(transactions).execute(tx -> {
			var company = Company.builder().companyCode("RD-ATOMIC-" + UUID.randomUUID()).companyName("추천 원자성 테스트").build();
			em.persist(company);
			var first = user(company); var second = user(company);
			var policy = Policy.builder().company(company).createdBy(first).taskType("INVOICE").build(); em.persist(policy);
			var recommendation = PolicyRecommendation.builder().policy(policy).status(RecommendationStatus.PENDING).nextAction("EXPAND").sampleCount(12).build();
			em.persist(recommendation);
			var base = PolicyVersion.builder().policy(policy).createdBy(first).status(PolicyVersionStatus.ACTIVE).versionName("existing").allowedTools("[]").build();
			em.persist(base);
			return new Fixture(company.getId(), first.getId(), second.getId(), policy.getId(), recommendation.getId(), base.getId());
		});
	}

	@AfterEach
	void cleanOnlyOwnCommittedFixture() {
		reset(recommendations);
		reset(versions);
		reset(audits);
		if (fixture != null) {
			new TransactionTemplate(transactions).executeWithoutResult(tx -> {
				jdbc.update("delete from audit_log where user_id in (?, ?)", fixture.first(), fixture.second());
				jdbc.update("delete from policy_recommendation where policy_id = ?", fixture.policy());
				jdbc.update("delete from policy_version where policy_id = ?", fixture.policy());
				jdbc.update("delete from policy where policy_id = ?", fixture.policy());
				jdbc.update("delete from users where user_id in (?, ?)", fixture.first(), fixture.second());
				jdbc.update("delete from company where company_id = ?", fixture.company());
			});
		}
	}

	@Test
	void simultaneousApprovalsFromTheSamePendingSnapshotCreateExactlyOneDraft() throws Exception {
		var barrier = new CyclicBarrier(2); policyReadBarrier(barrier);
		var outcomes = race(() -> approve(fixture.first()), () -> approve(fixture.second()));
		assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
		assertDecision("APPROVED", true, outcomes.get(0) == 200 ? fixture.first() : fixture.second());
	}

	@Test
	void simultaneousApprovalAndRejectionHaveOnlyOneWinner() throws Exception {
		var barrier = new CyclicBarrier(2); policyReadBarrier(barrier);
		var outcomes = race(() -> approve(fixture.first()), () -> reject(fixture.second()));
		assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
		boolean approved = outcomes.get(0) == 200;
		assertDecision(approved ? "APPROVED" : "REJECTED", approved, approved ? fixture.first() : fixture.second());
	}

	@Test
	void simultaneousRejectionsRecordOnlyOneAudit() throws Exception {
		var barrier = new CyclicBarrier(2); policyReadBarrier(barrier);
		var outcomes = race(() -> reject(fixture.first()), () -> reject(fixture.second()));
		assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
		assertDecision("REJECTED", false, outcomes.get(0) == 200 ? fixture.first() : fixture.second());
	}

	@Test
	void dashboardAndPolicyApprovalCompeteForTheSamePendingDecision() throws Exception {
		var barrier = new CyclicBarrier(2); policyReadBarrier(barrier); dashboardReadBarrier(barrier);
		var outcomes = race(() -> approve(fixture.first()), () -> dashboard(fixture.second()));
		assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
		boolean policyWon = outcomes.get(0) == 200;
		assertDecision("APPROVED", policyWon, policyWon ? fixture.first() : fixture.second());
		assertThat(events()).contains(policyWon ? "RECOMMENDATION_APPROVED" : "DECISION_RECORDED");
	}

	@Test
	void dashboardAndPolicyRejectionCannotBothCommit() throws Exception {
		var barrier = new CyclicBarrier(2); policyReadBarrier(barrier); dashboardReadBarrier(barrier);
		var outcomes = race(() -> reject(fixture.first()), () -> dashboard(fixture.second()));
		assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
		boolean policyWon = outcomes.get(0) == 200;
		assertDecision(policyWon ? "REJECTED" : "APPROVED", false, policyWon ? fixture.first() : fixture.second());
		assertThat(events()).containsExactly(policyWon ? "RECOMMENDATION_REJECTED" : "DECISION_RECORDED");
	}

	@Test
	void approvalAuditFailureRollsBackDecisionDraftLinkAndPolicyCreatedAudit() throws Exception {
		failAudit(AuditEventType.RECOMMENDATION_APPROVED);
		api("approve", "{\"versionName\":\"rollback\",\"allowedTools\":[]}");
		assertRolledBack();
	}

	@Test
	void rejectionAuditFailureRollsBackDecisionAndReason() throws Exception {
		failAudit(AuditEventType.RECOMMENDATION_REJECTED);
		api("reject", "{\"rejectionReason\":\"rollback\"}");
		assertRolledBack();
	}

	@Test
	void draftStorageFailureRollsBackClaimedDecision() throws Exception {
		doThrow(new IllegalStateException("draft storage unavailable")).when(versions).saveAndFlush(any(PolicyVersion.class));
		api("approve", "{\"versionName\":\"rollback\",\"allowedTools\":[]}");
		assertRolledBack();
	}

	private void policyReadBarrier(CyclicBarrier barrier) {
		doAnswer(call -> {
			var result = readDelegate.findRecommendationDetail(call.getArgument(0), call.getArgument(1));
			barrier.await(10, TimeUnit.SECONDS); return result;
		}).when(recommendations).findRecommendationDetail(eq(fixture.recommendation()), anyLong());
	}
	private void dashboardReadBarrier(CyclicBarrier barrier) {
		doAnswer(call -> {
			var result = readDelegate.findWithPolicyById(call.getArgument(0));
			barrier.await(10, TimeUnit.SECONDS); return result;
		}).when(recommendations).findWithPolicyById(fixture.recommendation());
	}
	private List<Integer> race(Runnable first, Runnable second) throws Exception {
		var executor = Executors.newFixedThreadPool(2);
		try {
			var a = executor.submit(outcome(first)); var b = executor.submit(outcome(second));
			return List.of(a.get(25, TimeUnit.SECONDS), b.get(25, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}
	private Callable<Integer> outcome(Runnable operation) {
		return () -> {
			try { operation.run(); return 200; }
			catch (CustomException e) {
				assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECOMMENDATION_ALREADY_DECIDED); return 409;
			}
		};
	}
	private void approve(long actor) {
		commands.approve(actor, fixture.recommendation(), new PolicyRecommendationApproveRequest("race-draft", mapper.readTree("[]"), null, null, null, null, null));
	}
	private void reject(long actor) { commands.reject(actor, fixture.recommendation(), new PolicyRecommendationRejectRequest("race rejection")); }
	private void dashboard(long actor) { dashboard.recordDecision(actor, new DecisionRecordRequest(fixture.recommendation(), DecisionResult.EXPAND, null)); }
	private void assertDecision(String expected, boolean hasDraft, long actor) {
		var row = jdbc.queryForMap("select * from policy_recommendation where recommendation_id = ?", fixture.recommendation());
		assertThat(row).containsEntry("status", expected).containsEntry("decided_by", actor);
		assertThat(row.get("decided_at")).isNotNull();
		assertThat(jdbc.queryForObject("select count(*) from policy_version where policy_id = ?", Long.class, fixture.policy())).isEqualTo(hasDraft ? 2 : 1);
		assertThat(events()).hasSize(hasDraft ? 2 : 1);
		if (hasDraft) {
			assertThat(row.get("generated_version_id")).isNotNull();
			assertThat(jdbc.queryForObject("select status from policy_version where version_id = ?", String.class, row.get("generated_version_id"))).isEqualTo("DRAFT");
		} else { assertThat(row.get("generated_version_id")).isNull(); }
	}
	private void failAudit(AuditEventType event) {
		doAnswer(call -> {
			AuditLog audit = call.getArgument(0);
			if (audit.getEventType() == event) { throw new IllegalStateException("audit storage unavailable"); }
			return call.callRealMethod();
		}).when(audits).save(any(AuditLog.class));
	}
	private void api(String operation, String body) throws Exception {
		mvc.perform(post("/api/v1/policy-recommendations/" + fixture.recommendation() + "/" + operation)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(fixture.first()).accessToken())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isInternalServerError());
	}
	private void assertRolledBack() {
		var row = jdbc.queryForMap("select * from policy_recommendation where recommendation_id = ?", fixture.recommendation());
		assertThat(row).containsEntry("status", "PENDING").containsEntry("decided_at", null)
				.containsEntry("decided_by", null).containsEntry("generated_version_id", null).containsEntry("rejection_reason", null);
		assertThat(jdbc.queryForObject("select count(*) from policy_version where policy_id = ?", Long.class, fixture.policy())).isEqualTo(1);
		assertThat(events()).isEmpty();
	}
	private List<String> events() {
		return jdbc.queryForList("select event_type from audit_log where user_id in (?, ?)", String.class, fixture.first(), fixture.second());
	}
	private User user(Company company) {
		var user = User.builder().company(company).employeeNumber(UUID.randomUUID().toString()).passwordHash("test-only")
				.userName("추천 동시성").roleType(RoleType.APPROVER).failedLoginCount(0).build();
		em.persist(user); return user;
	}
	record Fixture(long company, long first, long second, long policy, long recommendation, long base) {
	}
}
