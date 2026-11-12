package com.babylion.team1.domain.user.repository;

import com.babylion.team1.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
