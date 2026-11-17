package com.babylion.team1.domain.company.service;

import com.babylion.team1.domain.company.dto.response.AuthCompaniesResponse;
import com.babylion.team1.domain.company.dto.response.AuthCompaniesResponse.Item;
import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthCompaniesService {

	private final UserRepository userRepository;

	public AuthCompaniesResponse getCompanies(Long authenticatedUserId) {
		if (authenticatedUserId == null) {
			throw AuthException.unauthorized();
		}

		User user = userRepository.findById(authenticatedUserId)
				.orElseThrow(AuthException::unauthorized);
		Company company = user.getCompany();
		if (company == null) {
			return new AuthCompaniesResponse(List.of());
		}

		return new AuthCompaniesResponse(List.of(new Item(company.getCompanyCode(), company.getCompanyName())));
	}
}
