package com.babylion.team1.domain.policy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.audit.entity.AuditLog;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.policy.entity.Policy;
import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.auth.JwtTokens;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 테스트 자체의 트랜잭션 없이 실제 Service 트랜잭션의 롤백 완료 상태를 확인한다. */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyVersionDraftRollbackIntegrationTest {
	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry registry) {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		String secret = Base64.getEncoder().encodeToString(key);
		registry.add("jwt.secret", () -> secret);
	}

	@Autowired MockMvc mvc;
	@Autowired JwtTokens tokens;
	@Autowired EntityManager em;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactions;
	@MockitoSpyBean AuditLogRepository audits;

	@Test
	void auditFailureRollsBackInsertedVersionInRealDatabase() throws Exception {
		var transaction = new TransactionTemplate(transactions);
		var ids = transaction.execute(tx -> {
			var company = Company.builder().companyCode("PD-RB-" + UUID.randomUUID()).companyName("초안 롤백 테스트").build();
			em.persist(company);
			var user = User.builder().company(company).employeeNumber(UUID.randomUUID().toString()).passwordHash("test-hash")
					.userName("초안 롤백").roleType(RoleType.ADMIN).failedLoginCount(0).build();
			em.persist(user);
			var policy = Policy.builder().company(company).createdBy(user).taskType("INVOICE").build();
			em.persist(policy);
			var base = PolicyVersion.builder().policy(policy).createdBy(user).versionName("base")
					.status(PolicyVersionStatus.DRAFT).allowedTools("[]").build();
			em.persist(base);
			return new long[]{company.getId(), user.getId(), policy.getId(), base.getId()};
		});
		try {
			doThrow(new IllegalStateException("audit storage unavailable")).when(audits).save(any(AuditLog.class));
			mvc.perform(post("/api/v1/policies/" + ids[2] + "/versions")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(ids[1]).accessToken())
					.contentType(MediaType.APPLICATION_JSON).content("{\"versionName\":\"rollback-test\",\"allowedTools\":[]}"))
					.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
			assertThat(jdbc.queryForObject("select count(*) from policy_version where policy_id = ?", Long.class, ids[2])).isEqualTo(1);
			assertThat(jdbc.queryForObject("select version_id from policy_version where policy_id = ?", Long.class, ids[2])).isEqualTo(ids[3]);
			assertThat(jdbc.queryForObject("select count(*) from audit_log where user_id = ?", Long.class, ids[1])).isZero();
		} finally {
			reset(audits);
			transaction.executeWithoutResult(tx -> {
				jdbc.update("delete from audit_log where user_id = ?", ids[1]);
				jdbc.update("delete from policy_version where policy_id = ?", ids[2]);
				jdbc.update("delete from policy where policy_id = ?", ids[2]);
				jdbc.update("delete from users where user_id = ?", ids[1]);
				jdbc.update("delete from company where company_id = ?", ids[0]);
			});
		}
	}
}
