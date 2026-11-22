package com.babylion.team1.domain.team.service;

import com.babylion.team1.domain.team.dto.request.TeamCreateRequest;
import com.babylion.team1.domain.team.dto.response.MyTeamResponse;
import com.babylion.team1.domain.team.dto.response.TeamCreateResponse;
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
}
