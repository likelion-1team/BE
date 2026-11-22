package com.babylion.team1.domain.team.service;

import com.babylion.team1.domain.feedback.dto.response.GuidesResponse;
import com.babylion.team1.domain.feedback.dto.response.SharedCasesResponse;
import com.babylion.team1.domain.feedback.entity.FeedbackType;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.domain.team.dto.request.TeamCreateRequest;
import com.babylion.team1.domain.team.dto.request.TeamMemberAddRequest;
import com.babylion.team1.domain.team.dto.request.TeamSettingsUpdateRequest;
import com.babylion.team1.domain.team.dto.response.MyTeamResponse;
import com.babylion.team1.domain.team.dto.response.TeamCreateResponse;
import com.babylion.team1.domain.team.dto.response.TeamMemberAddResponse;
import com.babylion.team1.domain.team.dto.response.TeamMemberListResponse;
import com.babylion.team1.domain.team.dto.response.TeamSettingsResponse;
import com.babylion.team1.domain.team.dto.response.TeamSettingsUpdateResponse;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.team.repository.TeamTaskTypeRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
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
    private final TeamTaskTypeRepository teamTaskTypeRepository;
    private final FeedbackRepository feedbackRepository;

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
        Team team = findTeam(teamId);
        requireTeamLeader(teamId, requesterId);

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

    @Transactional(readOnly = true)
    public List<TeamMemberListResponse> getTeamMembers(Long teamId, Long userId) {
        findTeam(teamId);
        requireTeamLeader(teamId, userId);
        return teamMemberRepository.findTeamMembersWithSubmission(teamId);
    }

    @Transactional(readOnly = true)
    public SharedCasesResponse getSharedCases(Long teamId, Long userId, Long taskTypeId, String type) {
        findTeam(teamId);
        requireTeamLeader(teamId, userId);
        return SharedCasesResponse.from(feedbackRepository.findSharedCases(
                teamId, taskTypeId, type, FeedbackType.alternateValue(type)));
    }

    @Transactional(readOnly = true)
    public GuidesResponse getGuides(Long teamId, Long userId, String type) {
        findTeam(teamId);
        requireTeamMember(teamId, userId);
        return GuidesResponse.from(feedbackRepository.findGuides(teamId, type, FeedbackType.alternateValue(type)));
    }

    @Transactional(readOnly = true)
    public TeamSettingsResponse getSettings(Long teamId, Long userId) {
        Team team = findTeam(teamId);
        requireTeamMember(teamId, userId);
        TeamSettings settings = teamSettingsRepository.findByTeamId(teamId)
                .orElseGet(() -> TeamSettings.createDefault(team));
        return TeamSettingsResponse.from(settings, teamTaskTypeRepository.findByTeamIdAndIsActiveTrueOrderById(teamId));
    }

    @Transactional
    public TeamSettingsUpdateResponse updateSettings(Long teamId, Long userId, TeamSettingsUpdateRequest request) {
        // Serialize settings writes for this team, including activation and first-time creation.
        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        requireTeamLeader(teamId, userId);

        LocalDate targetFrom = parseTargetDate(request.targetFrom());
        LocalDate targetTo = parseTargetDate(request.targetTo());
        LocalDate onboardingStartDate = parseTargetDate(request.onboardingStartDate());
        if (targetFrom.isAfter(targetTo)
                || onboardingStartDate.isBefore(targetFrom) || onboardingStartDate.isAfter(targetTo)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        activateTaskType(team, request.taskTypes().get(0));
        TeamSettings settings = teamSettingsRepository.findByTeamId(teamId)
                .orElseGet(() -> TeamSettings.createDefault(team));
        Integer targetCount = request.targetCount() == null ? null : request.targetCount().intValueExact();
        settings.updateTarget(targetFrom, targetTo, onboardingStartDate, targetCount);
        teamSettingsRepository.save(settings);
        return new TeamSettingsUpdateResponse(settings.updatedAtInKst());
    }

    private void activateTaskType(Team team, TeamSettingsUpdateRequest.TaskType request) {
        Long teamId = team.getId();
        TeamTaskType selected = teamTaskTypeRepository.findByTeamIdAndName(teamId, request.name())
                .orElseGet(() -> TeamTaskType.builder()
                        .team(team)
                        .name(request.name())
                        .successDefinition(request.successDefinition())
                        .build());
        teamTaskTypeRepository.findByTeamIdAndIsActiveTrueOrderById(teamId).stream()
                .filter(active -> !Objects.equals(active.getId(), selected.getId()))
                .forEach(TeamTaskType::deactivate);
        selected.update(request.name(), request.successDefinition());
        selected.activate();
        teamTaskTypeRepository.save(selected);
    }

    private LocalDate parseTargetDate(String value) {
        if (value.length() != 10 || value.startsWith("0000-")) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
    }

    private TeamMember requireTeamMember(Long teamId, Long userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_A_MEMBER));
    }

    private void requireTeamLeader(Long teamId, Long userId) {
        if (!requireTeamMember(teamId, userId).isLeader()) {
            throw new CustomException(ErrorCode.LEADER_ONLY);
        }
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
