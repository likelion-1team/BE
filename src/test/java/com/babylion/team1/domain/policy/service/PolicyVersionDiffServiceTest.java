package com.babylion.team1.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.policy.repository.PolicyRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.domain.workrequest.entity.RouteType;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PolicyVersionDiffServiceTest {
	@Mock UserRepository users;
	@Mock PolicyVersionRepository versions;
	@Mock ProjectPolicyMappingRepository mappings;
	@Mock PolicyRepository policies;
	PolicyQueryService service;

	@BeforeEach
	void setUp() {
		service = new PolicyQueryService(users, versions, mappings, policies, JsonMapper.builder().build());
	}

	@Test
	void changesKeepFieldOrderAndOriginalJsonValueTypes() {
		var base = PolicyVersion.builder().allowedTools("[]").build();
		var target = PolicyVersion.builder().confidenceThreshold(new BigDecimal("85.25"))
				.allowedTools("[\"AI\"]").successDefinition("검토 통과").qualityTimeCriteria("{}")
				.exceptionConditions("[]").defaultRoute(RouteType.LLM).build();
		givenVersions(base, target);

		var response = service.getVersionDiff(1L, 20L, 101L, 102L);
		assertThat(response.policyId()).isEqualTo(20);
		assertThat(response.baseId()).isEqualTo(101);
		assertThat(response.targetId()).isEqualTo(102);
		assertThat(response.changes()).extracting("field").containsExactly("confidenceThreshold", "allowedTools",
				"successDefinition", "qualityTimeCriteria", "exceptionConditions", "defaultRoute");
		assertThat(response.changes().get(0).baseValue().isNull()).isTrue();
		assertThat(response.changes().get(0).targetValue().decimalValue()).isEqualByComparingTo("85.25");
		assertThat(response.changes().get(1).targetValue().isArray()).isTrue();
		assertThat(response.changes().get(2).targetValue().asString()).isEqualTo("검토 통과");
		assertThat(response.changes().get(3).targetValue().isObject()).isTrue();
		assertThat(response.changes().get(4).targetValue().isArray()).isTrue();
		assertThat(response.changes().get(5).targetValue().asString()).isEqualTo("LLM");
		verifyNoInteractions(mappings);
	}

	@Test
	void objectKeyOrderAndNumericScaleAreIgnoredIncludingNestedArrays() {
		var base = PolicyVersion.builder().confidenceThreshold(new BigDecimal("80.00")).allowedTools("[\"AI\"]")
				.qualityTimeCriteria("{\"a\":[1,{\"n\":2.00}],\"enabled\":true}").build();
		var target = PolicyVersion.builder().confidenceThreshold(new BigDecimal("80.0")).allowedTools("[\"AI\"]")
				.qualityTimeCriteria("{\"enabled\":true,\"a\":[1.0,{\"n\":2}]}").build();
		givenVersions(base, target);
		assertThat(service.getVersionDiff(1L, 20L, 101L, 102L).changes()).isEmpty();
	}

	@Test
	void decimalChangesAreNotLostThroughDoubleRounding() {
		var base = PolicyVersion.builder().allowedTools("[]").qualityTimeCriteria("{\"n\":1.000000000000000001}").build();
		var target = PolicyVersion.builder().allowedTools("[]").qualityTimeCriteria("{\"n\":1.000000000000000002}").build();
		givenVersions(base, target);
		var changes = service.getVersionDiff(1L, 20L, 101L, 102L).changes();
		assertThat(changes).singleElement().satisfies(change -> {
			assertThat(change.field()).isEqualTo("qualityTimeCriteria");
			assertThat(change.baseValue().path("n").decimalValue()).isEqualByComparingTo("1.000000000000000001");
			assertThat(change.targetValue().path("n").decimalValue()).isEqualByComparingTo("1.000000000000000002");
		});
	}

	@Test
	void arrayOrderMattersAndNestedChangesReturnWholeTopLevelField() {
		var base = PolicyVersion.builder().allowedTools("[\"AI\",\"ERP\"]")
				.qualityTimeCriteria("{\"nested\":{\"limit\":10},\"keep\":true}").build();
		var target = PolicyVersion.builder().allowedTools("[\"ERP\",\"AI\"]")
				.qualityTimeCriteria("{\"nested\":{\"limit\":11},\"keep\":true}").build();
		givenVersions(base, target);
		var changes = service.getVersionDiff(1L, 20L, 101L, 102L).changes();
		assertThat(changes).extracting("field").containsExactly("allowedTools", "qualityTimeCriteria");
		assertThat(changes.get(1).baseValue().path("nested").path("limit").intValue()).isEqualTo(10);
		assertThat(changes.get(1).targetValue().has("keep")).isTrue();
	}

	@Test
	void sqlAndJsonNullAreEqualButEmptyObjectsAndArraysAreDifferent() {
		var base = PolicyVersion.builder().allowedTools("[]").build();
		givenVersions(base, PolicyVersion.builder().allowedTools("[]").qualityTimeCriteria("null").build());
		assertThat(service.getVersionDiff(1L, 20L, 101L, 102L).changes()).isEmpty();
		given(versions.findVersionDetail(20L, 102L, 10L)).willReturn(Optional.of(
				PolicyVersion.builder().allowedTools("[]").qualityTimeCriteria("{}").exceptionConditions("[]").build()));
		var changes = service.getVersionDiff(1L, 20L, 101L, 102L).changes();
		assertThat(changes).extracting("field").containsExactly("qualityTimeCriteria", "exceptionConditions");
		assertThat(changes.get(0).baseValue().isNull()).isTrue();
		assertThat(changes.get(0).targetValue().isObject()).isTrue();
		assertThat(changes.get(1).targetValue().isArray()).isTrue();
	}

	@Test
	void missingObjectKeyIsDifferentFromExplicitNullAndValueTypesRemainDistinct() {
		var base = PolicyVersion.builder().allowedTools("[]").qualityTimeCriteria("{}")
				.exceptionConditions("{\"n\":1,\"flag\":true}").build();
		var target = PolicyVersion.builder().allowedTools("[]").successDefinition("").qualityTimeCriteria("{\"n\":null}")
				.exceptionConditions("{\"n\":\"1\",\"flag\":\"true\"}").build();
		givenVersions(base, target);
		assertThat(service.getVersionDiff(1L, 20L, 101L, 102L).changes()).extracting("field")
				.containsExactly("successDefinition", "qualityTimeCriteria", "exceptionConditions");
	}

	@Test
	void versionMetadataIsNotAComparedSetting() {
		givenVersions(PolicyVersion.builder().allowedTools("[]").versionName("v1").status(PolicyVersionStatus.ACTIVE).build(),
				PolicyVersion.builder().allowedTools("[]").versionName("v2").status(PolicyVersionStatus.DRAFT).build());
		assertThat(service.getVersionDiff(1L, 20L, 101L, 102L).changes()).isEmpty();
	}

	@Test
	void sameVersionIsValidatedAndLoadedOnlyOnce() {
		givenCompanyPolicy();
		given(versions.findVersionDetail(20L, 101L, 10L)).willReturn(Optional.of(PolicyVersion.builder().allowedTools("[]").build()));
		assertThat(service.getVersionDiff(1L, 20L, 101L, 101L).changes()).isEmpty();
		verify(versions).findVersionDetail(20L, 101L, 10L);
	}

	@Test
	void missingPolicyStopsBeforeVersionLookup() {
		givenUser(RoleType.ADMIN);
		assertThatThrownBy(() -> service.getVersionDiff(1L, 20L, 101L, 102L))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_NOT_FOUND));
		verifyNoInteractions(versions, mappings);
	}

	@Test
	void missingBaseStopsBeforeTargetLookup() {
		givenCompanyPolicy();
		assertThatThrownBy(() -> service.getVersionDiff(1L, 20L, 101L, 102L))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_VERSION_NOT_FOUND));
		verify(versions, never()).findVersionDetail(20L, 102L, 10L);
	}

	@Test
	void missingTargetUsesVersionNotFound() {
		givenCompanyPolicy();
		given(versions.findVersionDetail(20L, 101L, 10L)).willReturn(Optional.of(PolicyVersion.builder().allowedTools("[]").build()));
		assertThatThrownBy(() -> service.getVersionDiff(1L, 20L, 101L, 102L))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_VERSION_NOT_FOUND));
	}

	@Test
	void identityAndRoleAreCheckedBeforePolicyOrVersions() {
		assertThatThrownBy(() -> service.getVersionDiff(null, 20L, 101L, 102L)).isInstanceOf(AuthException.class);
		givenUser(RoleType.USER);
		assertThatThrownBy(() -> service.getVersionDiff(1L, 20L, 101L, 102L))
				.isInstanceOfSatisfying(CustomException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_ACCESS_DENIED));
		verifyNoInteractions(policies, versions, mappings);
	}

	@Test
	void malformedStoredJsonIsNotSilentlyIgnored() {
		givenVersions(PolicyVersion.builder().allowedTools("{broken").build(), PolicyVersion.builder().allowedTools("[]").build());
		assertThatThrownBy(() -> service.getVersionDiff(1L, 20L, 101L, 102L)).isInstanceOf(JacksonException.class);
	}

	private void givenVersions(PolicyVersion base, PolicyVersion target) {
		givenCompanyPolicy();
		given(versions.findVersionDetail(20L, 101L, 10L)).willReturn(Optional.of(base));
		given(versions.findVersionDetail(20L, 102L, 10L)).willReturn(Optional.of(target));
	}

	private void givenCompanyPolicy() {
		givenUser(RoleType.ADMIN);
		given(policies.existsByIdAndCompany_Id(20L, 10L)).willReturn(true);
	}

	private void givenUser(RoleType role) {
		var company = Company.builder().companyCode("A").companyName("A").build();
		ReflectionTestUtils.setField(company, "id", 10L);
		given(users.findById(1L)).willReturn(Optional.of(User.builder().company(company).roleType(role).build()));
	}
}
