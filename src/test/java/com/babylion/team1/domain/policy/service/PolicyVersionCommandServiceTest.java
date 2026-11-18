package com.babylion.team1.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.babylion.team1.domain.audit.entity.AuditEventType;
import com.babylion.team1.domain.audit.entity.AuditLog;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.policy.dto.request.PolicyVersionDraftRequest;
import com.babylion.team1.domain.policy.entity.Policy;
import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.policy.repository.PolicyRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PolicyVersionCommandServiceTest {
	@Mock UserRepository users;
	@Mock PolicyRepository policies;
	@Mock PolicyVersionRepository versions;
	@Mock AuditLogRepository audits;
	private final JsonMapper mapper = JsonMapper.builder().build();
	private PolicyVersionCommandService service;

	@BeforeEach
	void setUp() {
		service = new PolicyVersionCommandService(users, policies, versions, audits, mapper);
	}

	@Test
	void missingIdentityIsRejectedBeforeQuerying() {
		assertThatThrownBy(() -> service.createDraft(null, 20L, request())).isInstanceOf(AuthException.class);
		verifyNoInteractions(users, policies, versions, audits);
	}

	@Test
	void missingUserOrCompanyIsRejected() {
		given(users.findById(1L)).willReturn(Optional.empty());
		given(users.findById(2L)).willReturn(Optional.of(User.builder().roleType(RoleType.ADMIN).build()));
		assertThatThrownBy(() -> service.createDraft(1L, 20L, request())).isInstanceOf(AuthException.class);
		assertThatThrownBy(() -> service.createDraft(2L, 20L, request())).isInstanceOf(AuthException.class);
		verifyNoInteractions(policies, versions, audits);
	}

	@Test
	void ordinaryUserCannotCreateDraft() {
		givenUser(RoleType.USER);
		assertThatThrownBy(() -> service.createDraft(1L, 20L, request()))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_DRAFT_ACCESS_DENIED));
		verifyNoInteractions(policies, versions, audits);
	}

	@Test
	void policyIsLookedUpWithinAuthenticatedCompany() {
		givenUser(RoleType.ADMIN);
		assertThatThrownBy(() -> service.createDraft(1L, 20L, request()))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_NOT_FOUND));
		verify(policies).findByIdAndCompany_Id(20L, 10L);
		verifyNoInteractions(versions, audits);
	}

	@Test
	void invalidBaseIsRejectedBeforeSaving() {
		givenPolicy(RoleType.ADMIN);
		var request = mapper.readValue("{\"baseVersionId\":101,\"versionName\":\"v2\",\"allowedTools\":[]}", PolicyVersionDraftRequest.class);
		assertThatThrownBy(() -> service.createDraft(1L, 20L, request))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_VERSION_NOT_FOUND));
		verify(versions).existsByIdAndPolicy_IdAndPolicy_Company_Id(101L, 20L, 10L);
		verify(versions, never()).saveAndFlush(any());
		verifyNoInteractions(audits);
	}

	@Test
	void fullDraftUsesServerMetadataAndRecordsAuditWithValidatedBase() {
		User user = givenPolicy(RoleType.ADMIN);
		given(versions.existsByIdAndPolicy_IdAndPolicy_Company_Id(101L, 20L, 10L)).willReturn(true);
		givenSavedVersion();
		var request = mapper.readValue("""
				{"baseVersionId":101,"versionName":"v2","allowedTools":["AI","ERP"],"confidenceThreshold":85.25,
				 "successDefinition":"검토 통과","qualityTimeCriteria":{"seconds":30},"exceptionConditions":[],"defaultRoute":"LLM"}
				""", PolicyVersionDraftRequest.class);
		var response = service.createDraft(1L, 20L, request);
		var saved = ArgumentCaptor.forClass(PolicyVersion.class);
		verify(versions).saveAndFlush(saved.capture());
		var version = saved.getValue();
		assertThat(version.getStatus()).isEqualTo(PolicyVersionStatus.DRAFT);
		assertThat(version.getCreatedBy()).isSameAs(user);
		assertThat(mapper.readTree(version.getAllowedTools())).isEqualTo(request.allowedTools());
		assertThat(version.getConfidenceThreshold()).isEqualByComparingTo("85.25");
		assertThat(version.getApprovedBy()).isNull();
		assertThat(version.getAppliedAt()).isNull();
		assertThat(version.getRolledBackAt()).isNull();
		assertThat(version.getRollbackReason()).isNull();
		assertThat(response.versionId()).isEqualTo(102);
		assertThat(response.createdBy()).isEqualTo(1);
		assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 12, 0));
		var audit = ArgumentCaptor.forClass(AuditLog.class);
		verify(audits).save(audit.capture());
		assertThat(audit.getValue().getUser()).isSameAs(user);
		assertThat(audit.getValue().getEventType()).isEqualTo(AuditEventType.POLICY_CREATED);
		assertThat(mapper.readTree(audit.getValue().getDetail()).path("versionId").longValue()).isEqualTo(102);
		assertThat(mapper.readTree(audit.getValue().getDetail()).path("baseVersionId").longValue()).isEqualTo(101);
	}

	@Test
	void approverCanCreateWithoutBaseAndOptionalNullJsonUsesSqlNull() {
		givenPolicy(RoleType.APPROVER);
		givenSavedVersion();
		var request = mapper.readValue("{\"versionName\":\"v2\",\"allowedTools\":[],\"qualityTimeCriteria\":null}", PolicyVersionDraftRequest.class);
		service.createDraft(1L, 20L, request);
		verify(versions, never()).existsByIdAndPolicy_IdAndPolicy_Company_Id(any(), any(), any());
		var saved = ArgumentCaptor.forClass(PolicyVersion.class);
		verify(versions).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getQualityTimeCriteria()).isNull();
		assertThat(saved.getValue().getExceptionConditions()).isNull();
		assertThat(saved.getValue().getDefaultRoute()).isNull();
	}

	@Test
	void auditFailurePropagatesToTransactionalBoundary() {
		givenPolicy(RoleType.ADMIN);
		givenSavedVersion();
		given(audits.save(any(AuditLog.class))).willThrow(new IllegalStateException("audit unavailable"));
		assertThatThrownBy(() -> service.createDraft(1L, 20L, request())).isInstanceOf(IllegalStateException.class);
	}

	private PolicyVersionDraftRequest request() {
		return mapper.readValue("{\"versionName\":\"v2\",\"allowedTools\":[]}", PolicyVersionDraftRequest.class);
	}

	private User givenPolicy(RoleType role) {
		var user = givenUser(role);
		var policy = Policy.builder().company(user.getCompany()).createdBy(user).taskType("INVOICE").build();
		ReflectionTestUtils.setField(policy, "id", 20L);
		given(policies.findByIdAndCompany_Id(20L, 10L)).willReturn(Optional.of(policy));
		return user;
	}

	private User givenUser(RoleType role) {
		var company = Company.builder().companyCode("A").companyName("A").build();
		ReflectionTestUtils.setField(company, "id", 10L);
		var user = User.builder().company(company).roleType(role).build();
		ReflectionTestUtils.setField(user, "id", 1L);
		given(users.findById(1L)).willReturn(Optional.of(user));
		return user;
	}

	private void givenSavedVersion() {
		given(versions.saveAndFlush(any(PolicyVersion.class))).willAnswer(invocation -> {
			PolicyVersion version = invocation.getArgument(0);
			ReflectionTestUtils.setField(version, "id", 102L);
			ReflectionTestUtils.setField(version, "createdAt", LocalDateTime.of(2026, 11, 18, 12, 0));
			return version;
		});
	}
}
