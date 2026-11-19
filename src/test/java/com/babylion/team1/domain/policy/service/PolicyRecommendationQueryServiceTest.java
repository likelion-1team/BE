package com.babylion.team1.domain.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.policy.dto.request.PolicyRecommendationListRequest;
import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import com.babylion.team1.domain.policy.entity.Policy;
import com.babylion.team1.domain.policy.entity.PolicyRecommendation;
import com.babylion.team1.domain.policy.repository.PolicyRecommendationRepository;
import com.babylion.team1.domain.policy.repository.PolicyRecommendationRepository.RecommendationListRow;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PolicyRecommendationQueryServiceTest {
	@Mock private UserRepository users;
	@Mock private PolicyRecommendationRepository recommendations;
	private PolicyRecommendationQueryService service;

	@BeforeEach
	void mapper() {
		service = new PolicyRecommendationQueryService(users, recommendations, new ObjectMapper());
	}

	@Test
	void missingIdentityIsRejectedForBothReads() {
		assertThatThrownBy(() -> service.getRecommendations(null, request())).isInstanceOf(AuthException.class);
		assertThatThrownBy(() -> service.getRecommendationDetail(null, 1L)).isInstanceOf(AuthException.class);
		verifyNoInteractions(users, recommendations);
	}

	@Test
	void deletedUserAndMissingCompanyAreUnauthorizedForBothReads() {
		given(users.findById(1L)).willReturn(Optional.empty());
		given(users.findById(2L)).willReturn(Optional.of(User.builder().roleType(RoleType.ADMIN).build()));
		for (long id : List.of(1L, 2L)) {
			assertThatThrownBy(() -> service.getRecommendations(id, request())).isInstanceOf(AuthException.class);
			assertThatThrownBy(() -> service.getRecommendationDetail(id, 1L)).isInstanceOf(AuthException.class);
		}
		verifyNoInteractions(recommendations);
	}

	@Test
	void ordinaryUserIsRejectedBeforeQueryingRecommendations() {
		givenUser(RoleType.USER);
		assertThatThrownBy(() -> service.getRecommendations(1L, request())).isInstanceOfSatisfying(CustomException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_ACCESS_DENIED));
		assertThatThrownBy(() -> service.getRecommendationDetail(1L, 2L)).isInstanceOfSatisfying(CustomException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POLICY_ACCESS_DENIED));
		verifyNoInteractions(recommendations);
	}

	@Test
	void approverUsesAuthenticatedCompanyAndCombinedFilters() {
		givenUser(RoleType.APPROVER);
		var row = mock(RecommendationListRow.class);
		given(row.getRecommendationId()).willReturn(21L);
		given(row.getPolicyId()).willReturn(7L);
		given(row.getTaskType()).willReturn("INVOICE");
		given(row.getStatus()).willReturn(RecommendationStatus.PENDING);
		given(row.getObservedProblem()).willReturn("불필요 호출 증가");
		given(row.getSampleCount()).willReturn(12);
		var createdAt = LocalDateTime.of(2026, 11, 1, 10, 0);
		given(row.getCreatedAt()).willReturn(createdAt);
		given(recommendations.findRecommendationList(10L, 7L, RecommendationStatus.PENDING, PageRequest.of(2, 3)))
				.willReturn(new PageImpl<>(List.of(row), PageRequest.of(2, 3), 7));
		var response = service.getRecommendations(1L, new PolicyRecommendationListRequest(7L, RecommendationStatus.PENDING, 2, 3));
		assertThat(response.page()).isEqualTo(2);
		assertThat(response.size()).isEqualTo(3);
		assertThat(response.totalElements()).isEqualTo(7);
		assertThat(response.items()).singleElement().satisfies(item -> {
			assertThat(item.recommendationId()).isEqualTo(21L);
			assertThat(item.policyId()).isEqualTo(7L);
			assertThat(item.taskType()).isEqualTo("INVOICE");
			assertThat(item.status()).isEqualTo(RecommendationStatus.PENDING);
			assertThat(item.observedProblem()).isEqualTo("불필요 호출 증가");
			assertThat(item.sampleCount()).isEqualTo(12);
			assertThat(item.createdAt()).isEqualTo(createdAt);
		});
	}

	@Test
	void emptyOutOfRangePageKeepsFilteredTotal() {
		givenUser(RoleType.ADMIN);
		given(recommendations.findRecommendationList(10L, null, null, PageRequest.of(9, 2)))
				.willReturn(new PageImpl<>(List.of(), PageRequest.of(9, 2), 3));
		var response = service.getRecommendations(1L, new PolicyRecommendationListRequest(null, null, 9, 2));
		assertThat(response.items()).isEmpty();
		assertThat(response.page()).isEqualTo(9);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(3);
	}

	@Test
	void missingOrForeignDetailUsesScopedQueryAndDomain404() {
		givenUser(RoleType.ADMIN);
		given(recommendations.findRecommendationDetail(21L, 10L)).willReturn(Optional.empty());
		assertThatThrownBy(() -> service.getRecommendationDetail(1L, 21L)).isInstanceOfSatisfying(CustomException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECOMMENDATION_NOT_FOUND));
	}

	@Test
	void detailPreservesJsonNumbersAndDecisionMetadata() {
		givenUser(RoleType.APPROVER);
		var policy = Policy.builder().taskType("INVOICE").build();
		ReflectionTestUtils.setField(policy, "id", 7L);
		var actor = User.builder().userName("결정자").build();
		ReflectionTestUtils.setField(actor, "id", 9L);
		var time = LocalDateTime.of(2026, 11, 1, 10, 0);
		var recommendation = PolicyRecommendation.builder().policy(policy).status(RecommendationStatus.REJECTED)
				.observedProblem("관찰 문제").causeCandidate("원인 후보").nextAction("다음 조치")
				.detail("{\"rate\":0.1234567890123456789,\"tools\":[\"AI\",\"ERP\"]}")
				.sampleCount(12).rejectionReason("반려 사유").decidedBy(actor).decidedAt(time).build();
		ReflectionTestUtils.setField(recommendation, "id", 21L);
		ReflectionTestUtils.setField(recommendation, "createdAt", time.minusDays(1));
		given(recommendations.findRecommendationDetail(21L, 10L)).willReturn(Optional.of(recommendation));
		var response = service.getRecommendationDetail(1L, 21L);
		assertThat(response.recommendationId()).isEqualTo(21L);
		assertThat(response.policyId()).isEqualTo(7L);
		assertThat(response.status()).isEqualTo(RecommendationStatus.REJECTED);
		assertThat(response.observedProblem()).isEqualTo("관찰 문제");
		assertThat(response.causeCandidate()).isEqualTo("원인 후보");
		assertThat(response.nextAction()).isEqualTo("다음 조치");
		assertThat(response.detail().path("rate").decimalValue()).isEqualByComparingTo("0.1234567890123456789");
		assertThat(response.detail().path("tools").isArray()).isTrue();
		assertThat(response.sampleCount()).isEqualTo(12);
		assertThat(response.rejectionReason()).isEqualTo("반려 사유");
		assertThat(response.decidedBy()).isEqualTo(9L);
		assertThat(response.decidedAt()).isEqualTo(time);
		assertThat(response.createdAt()).isEqualTo(time.minusDays(1));
	}

	@Test
	void pendingDetailPreservesSqlAndJsonNullWithoutEmptyObjectFallback() {
		givenUser(RoleType.ADMIN);
		var policy = Policy.builder().build();
		for (String json : new String[]{null, "null"}) {
			var recommendation = PolicyRecommendation.builder().policy(policy).status(RecommendationStatus.PENDING).detail(json).sampleCount(0).build();
			given(recommendations.findRecommendationDetail(21L, 10L)).willReturn(Optional.of(recommendation));
			var response = service.getRecommendationDetail(1L, 21L);
			assertThat(response.detail() == null || response.detail().isNull()).isTrue();
			assertThat(response.decidedBy()).isNull();
			assertThat(response.decidedAt()).isNull();
			assertThat(response.rejectionReason()).isNull();
		}
	}

	private PolicyRecommendationListRequest request() {
		return new PolicyRecommendationListRequest(null, null, null, null);
	}

	private void givenUser(RoleType role) {
		var company = Company.builder().companyCode("TEST").companyName("테스트 회사").build();
		ReflectionTestUtils.setField(company, "id", 10L);
		given(users.findById(1L)).willReturn(Optional.of(User.builder().company(company).roleType(role).build()));
	}
}
