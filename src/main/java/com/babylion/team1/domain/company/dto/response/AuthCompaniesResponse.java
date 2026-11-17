package com.babylion.team1.domain.company.dto.response;

import java.util.List;

public record AuthCompaniesResponse(List<Item> items) {

	public record Item(String companyCode, String companyName) {
	}
}
