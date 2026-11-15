package com.babylion.team1.domain.company.repository;

import com.babylion.team1.domain.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
