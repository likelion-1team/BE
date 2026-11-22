package com.babylion.team1.domain.team.service;

import com.babylion.team1.domain.team.dto.request.TeamCreateRequest;
import com.babylion.team1.domain.team.dto.request.TeamMemberAddRequest;
import com.babylion.team1.domain.team.dto.response.MyTeamResponse;
import com.babylion.team1.domain.team.dto.response.TeamCreateResponse;
import com.babylion.team1.domain.team.dto.response.TeamMemberAddResponse;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamSettingsRepository teamSettingsRepository;

    @Transactional
    public TeamCreateResponse createTeam(Long userId, TeamCreateRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Team team = teamRepository.save(Team.builder()
                .name(request.name())
                .description(request.description())
                .createdBy(creator)
                .build());
        teamMemberRepository.save(TeamMember.leader(team, creator));
        teamSettingsRepository.save(TeamSettings.createDefault(team));
        return new TeamCreateResponse(team.getId());
    }

    @Transactional(readOnly = true)
    public List<MyTeamResponse> getMyTeams(Long userId) {
        return teamMemberRepository.findMyTeams(userId);
    }

    @Transactional
    public TeamMemberAddResponse addMember(Long teamId, Long requesterId, TeamMemberAddRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        TeamMember requester = teamMemberRepository.findByTeamIdAndUserId(teamId, requesterId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_A_MEMBER));
        if (!requester.isLeader()) {
            throw new CustomException(ErrorCode.LEADER_ONLY);
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "가입되지 않은 이메일입니다."));
        TeamMember membership = TeamMember.member(team, user);
        try {
            teamMemberRepository.saveAndFlush(membership);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateMembership(exception)) {
                throw new CustomException(ErrorCode.ALREADY_MEMBER);
            }
            throw exception;
        }

        return new TeamMemberAddResponse(user.getId(), user.getName(), membership.getRole());
    }

    private boolean isDuplicateMembership(DataIntegrityViolationException exception) {
        if (!(exception.getCause() instanceof ConstraintViolationException violation)) {
            return false;
        }
        String constraintName = violation.getConstraintName();
        // MySQL may prefix the constraint name with the table name.
        return constraintName != null && TeamMember.TEAM_USER_UNIQUE_CONSTRAINT.equals(
                constraintName.substring(constraintName.lastIndexOf('.') + 1));
    }
}
