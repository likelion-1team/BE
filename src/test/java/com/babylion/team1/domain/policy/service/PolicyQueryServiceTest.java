package com.babylion.team1.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.policy.dto.request.PolicyListRequest;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository.PolicyVersionListRow;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository.VersionProjectCount;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PolicyQueryServiceTest {
	@Mock private UserRepository users;
	@Mock private PolicyVersionRepository versions;
	@Mock private ProjectPolicyMappingRepository mappings;
	@InjectMocks private PolicyQueryService service;

	@Test
	void missingIdentityIsRejectedBeforeQuerying() {
		assertThatThrownBy(() -> service.getPolicies(null, request())).isInstanceOf(AuthException.class);
		verifyNoInteractions(users, versions, mappings);
	}

	@Test
	void deletedUserOrUserWithoutCompanyIsRejected() {
		given(users.findById(1L)).willReturn(Optional.empty());
		given(users.findById(2L)).willReturn(Optional.of(User.builder().roleType(RoleType.ADMIN).build()));
		assertThatThrownBy(() -> service.getPolicies(1L, request())).isInstanceOf(AuthException.class);
		assertThatThrownBy(() -> service.getPolicies(2L, request())).isInstanceOf(AuthException.class);
		verifyNoInteractions(versions, mappings);
	}

	@Test
	void ordinaryUserCannotQueryCompanyPolicies() {
		givenUser(RoleType.USER);
		assertThatThrownBy(() -> service.getPolicies(1L, request()))
				.isInstanceOfSatisfying(CustomException.class,
						error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.POLICY_ACCESS_DENIED));
		verifyNoInteractions(versions, mappings);
	}

	@Test
	void emptyPageKeepsTotalAndSkipsAggregation() {
		givenUser(RoleType.ADMIN);
		var request = new PolicyListRequest(null, null, 3, 2);
		given(versions.findPolicyList(10L, null, request.statuses(), PageRequest.of(3, 2)))
				.willReturn(new PageImpl<>(List.of(), PageRequest.of(3, 2), 5));
		var response = service.getPolicies(1L, request);
		assertThat(response.items()).isEmpty();
		assertThat(response.totalElements()).isEqualTo(5);
		assertThat(response.page()).isEqualTo(3);
		assertThat(response.size()).isEqualTo(2);
		verifyNoInteractions(mappings);
	}

	@Test
	void approverQueriesOnlyItsCompanyAndAggregatesCurrentPageOnce() {
		givenUser(RoleType.APPROVER);
		var request = new PolicyListRequest(" INVOICE ", PolicyVersionStatus.DRAFT, null, null);
		var first = row(101L);
		var second = row(102L);
		given(versions.findPolicyList(10L, "INVOICE", List.of(PolicyVersionStatus.DRAFT), PageRequest.of(0, 20)))
				.willReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2));
		var count = mock(VersionProjectCount.class);
		given(count.getVersionId()).willReturn(101L);
		given(count.getProjectCount()).willReturn(2L);
		given(mappings.countProjectsByVersionIds(List.of(101L, 102L), 10L)).willReturn(List.of(count));

		var response = service.getPolicies(1L, request);
		assertThat(response.items()).extracting("versionId", "projectCount")
				.containsExactly(org.assertj.core.groups.Tuple.tuple(101L, 2L),
						org.assertj.core.groups.Tuple.tuple(102L, 0L));
		verify(mappings).countProjectsByVersionIds(List.of(101L, 102L), 10L);
	}

	@Test
	void blankFilterUsesDefaultsAndExplicitDraftRemainsSelectable() {
		var defaults = new PolicyListRequest(" \t ", null, null, null);
		assertThat(defaults.taskType()).isNull();
		assertThat(defaults.page()).isZero();
		assertThat(defaults.size()).isEqualTo(20);
		assertThat(defaults.statuses()).containsExactly(PolicyVersionStatus.ACTIVE, PolicyVersionStatus.ROLLED_BACK);
		assertThat(new PolicyListRequest(null, PolicyVersionStatus.DRAFT, 0, 1).statuses())
				.containsExactly(PolicyVersionStatus.DRAFT);
	}

	private void givenUser(RoleType role) {
		var company = Company.builder().companyCode("A").companyName("A").build();
		ReflectionTestUtils.setField(company, "id", 10L);
		given(users.findById(1L)).willReturn(Optional.of(User.builder().company(company).roleType(role).build()));
	}

	private PolicyVersionListRow row(long versionId) {
		var row = mock(PolicyVersionListRow.class);
		given(row.getVersionId()).willReturn(versionId);
		return row;
	}

	private PolicyListRequest request() {
		return new PolicyListRequest(null, null, null, null);
	}
}
