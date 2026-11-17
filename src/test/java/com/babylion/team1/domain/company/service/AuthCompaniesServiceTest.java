package com.babylion.team1.domain.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.babylion.team1.domain.company.dto.response.AuthCompaniesResponse.Item;
import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthCompaniesServiceTest {

	private static final Long USER_ID = 42L;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private AuthCompaniesService service;

	@Test
	void returnsOnlyTheAuthenticatedUsersCompany() {
		Company company = Company.builder().companyCode("DEMO").companyName("데모 고객사").build();
		User user = User.builder().company(company).build();
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

		assertThat(service.getCompanies(USER_ID).items())
				.containsExactly(new Item("DEMO", "데모 고객사"));
	}

	@Test
	void returnsEmptyItemsWhenTheUserHasNoCompany() {
		given(userRepository.findById(USER_ID)).willReturn(Optional.of(User.builder().build()));

		assertThat(service.getCompanies(USER_ID).items()).isEmpty();
	}

	@Test
	void rejectsMissingAuthenticatedUserId() {
		assertThatThrownBy(() -> service.getCompanies(null))
				.isInstanceOfSatisfying(AuthException.class,
						error -> assertThat(error.getCode()).isEqualTo("UNAUTHORIZED"));
		verifyNoInteractions(userRepository);
	}

	@Test
	void rejectsUnknownAuthenticatedUser() {
		given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.getCompanies(USER_ID))
				.isInstanceOfSatisfying(AuthException.class,
						error -> assertThat(error.getCode()).isEqualTo("UNAUTHORIZED"));
	}
}
