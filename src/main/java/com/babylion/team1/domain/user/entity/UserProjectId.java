package com.babylion.team1.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** User_Project 복합키 (user_id + project_id) */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserProjectId implements Serializable {

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "project_id")
	private Long projectId;
}
