package com.babylion.team1.domain.user.repository;

import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<User> findTop2ByCompany_IdAndEmployeeNumber(Long companyId, String employeeNumber);
}
