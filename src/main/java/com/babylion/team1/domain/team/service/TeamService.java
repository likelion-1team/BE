package com.babylion.team1.domain.team.service;

import com.babylion.team1.domain.feedback.dto.response.GuideAddResponse;
import com.babylion.team1.domain.feedback.dto.response.MyFeedbackListResponse;
import com.babylion.team1.domain.feedback.dto.response.SharedCasesResponse;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.FeedbackType;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.domain.feedback.repository.GuideRepository;
import com.babylion.team1.domain.feedback.repository.SharedCaseRepository;
import com.babylion.team1.domain.feedback.entity.SharedCase;
import com.babylion.team1.domain.feedback.entity.Guide;
import com.babylion.team1.domain.team.dto.request.TeamCreateRequest;
import com.babylion.team1.domain.team.dto.request.TeamMemberAddRequest;
import com.babylion.team1.domain.team.dto.response.MyTeamsResponse;
import com.babylion.team1.domain.team.dto.response.TeamCreateResponse;
import com.babylion.team1.domain.team.dto.response.TeamMemberAddResponse;
import com.babylion.team1.domain.team.dto.response.TeamMembersResponse;
import com.babylion.team1.domain.team.dto.response.TeamSubmissionResponse;
import com.babylion.team1.domain.team.dto.response.TeamSubmissionResponse.Status;
import com.babylion.team1.global.policy.ServerPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamGoal;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamGoalRepository;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamService {

    private static final int MAX_TEAM_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamGoalRepository teamGoalRepository;
    private final TeamSettingsRepository teamSettingsRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackEvidenceRepository feedbackEvidenceRepository;
    private final SharedCaseRepository sharedCases;
    private final GuideRepository guides;

    @Transactional
    public TeamCreateResponse createTeam(Long userId, TeamCreateRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Team team = teamRepository.save(Team.builder()
                .name(request.name())
                .description(request.description())
                .createdBy(creator)
                .build());
        TeamMember leader = teamMemberRepository.save(TeamMember.leader(team, creator));
        teamSettingsRepository.save(TeamSettings.createDefault(team));
        teamGoalRepository.save(TeamGoal.createDefault(team));
        return new TeamCreateResponse(team.getId(), team.getName(), team.getDescription(), leader.getRole());
    }

    @Transactional(readOnly = true)
    public MyTeamsResponse getMyTeams(Long userId, int page, int size) {
        var teams = teamMemberRepository.findMyTeams(userId, teamPageRequest(page, size));
        return new MyTeamsResponse(teams.getContent(), page, size, teams.getTotalElements());
    }

    @Transactional
    public TeamMemberAddResponse addMember(Long teamId, Long requesterId, TeamMemberAddRequest request) {
        TeamMember requester = requireMembershipManager(teamId, requesterId);

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "가입되지 않은 이메일입니다."));
        TeamMember membership = TeamMember.member(requester.getTeam(), user);
        try {
            teamMemberRepository.saveAndFlush(membership);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateMembership(exception)) {
                throw new CustomException(ErrorCode.ALREADY_MEMBER);
            }
            throw exception;
        }

        return new TeamMemberAddResponse(user.getId(), user.getName(), user.getEmail(), membership.getRole());
    }

    @Transactional(readOnly = true)
    public TeamMembersResponse getTeamMembers(Long teamId, Long userId, int page, int size) {
        requireMembershipManager(teamId, userId);
        var members = teamMemberRepository.findTeamMembersWithSubmission(teamId, teamPageRequest(page, size));
        return new TeamMembersResponse(members.getContent(), page, size, members.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TeamSubmissionResponse getTeamSubmissions(Long teamId, Long userId) {
        var membership = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!membership.isLeader()) {
            throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
        }
        var summary = teamMemberRepository.findTeamSubmissionSummary(teamId);
        if (summary.contributors() < ServerPolicy.MIN_TEAM_CONTRIBUTORS) {
            return new TeamSubmissionResponse(summary.settingsVersion(), summary.submittedMembers(),
                    summary.totalMembers(), null, null, null, Status.SUPPRESSED);
        }
        Integer targetCount = summary.targetCount();
        BigDecimal coverageRatio = targetCount == null || targetCount <= 0 ? null
                : BigDecimal.valueOf(summary.analyzedConversations())
                        .divide(BigDecimal.valueOf(targetCount), 8, RoundingMode.HALF_UP).stripTrailingZeros();
        return new TeamSubmissionResponse(summary.settingsVersion(), summary.submittedMembers(),
                summary.totalMembers(), summary.analyzedConversations(), targetCount, coverageRatio, Status.AVAILABLE);
    }

    @Transactional(readOnly = true)
    public MyFeedbackListResponse getMyFeedbacks(Long teamId, Long userId, LocalDate from, LocalDate to,
                                                  String type, String sort, int page, int size) {
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (page < 1 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to))
                || !("LATEST".equals(sort) || "FREQUENCY".equals(sort))) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        var rows = feedbackRepository.findVerifiedFeedback(teamId, userId, from, to, type,
                FeedbackType.alternateValue(type), "FREQUENCY".equals(sort), PageRequest.of(page - 1, size));
        return new MyFeedbackListResponse(rows.stream().map(row -> new MyFeedbackListResponse.Item(
                row.getFeedbackId(), row.getItemId(), row.getConversationId(), row.getConversationVersionId(),
                row.getType(), row.getTitle(),
                row.getOccurrenceCount(), row.getCreatedAt().atZone(java.time.ZoneId.systemDefault())
                        .withZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                row.getReaction(), row.getSharingStatus(),
                com.babylion.team1.domain.feedback.dto.response.FeedbackDetailResponse.ExpectedSaving.NOT_MEASURED))
                .toList(), page, size, rows.getTotalElements());
    }

    @Transactional(readOnly = true)
    public SharedCasesResponse getSharedCases(Long teamId, Long userId, String type, int page, int size) {
        requireMembershipManager(teamId, userId);
        return SharedCasesResponse.from(sharedCases.findPublicCases(teamId, type, FeedbackType.alternateValue(type),
                false, teamPageRequest(page, size)));
    }

    @Transactional(readOnly = true)
    public SharedCasesResponse getGuides(Long teamId, Long userId, String type, int page, int size) {
        teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        return SharedCasesResponse.from(sharedCases.findPublicCases(teamId, type, FeedbackType.alternateValue(type),
                true, teamPageRequest(page, size)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GuideAddResponse addGuide(Long teamId, Long sharedCaseId, Long userId, int expectedShareVersion) {
        requireMembershipManager(teamId, userId);
        Feedback feedback = lockSharedCaseFeedback(teamId, sharedCaseId);
        SharedCase sharedCase = sharedCases.findByIdAndTeamId(sharedCaseId, teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (sharedCase.getStatus() == SharedCase.Status.REVOKED) {
            throw new CustomException(ErrorCode.SHARING_REVOKED);
        }
        if (sharedCase.getStatus() != SharedCase.Status.ACTIVE
                || feedbackEvidenceRepository.hasDeletedOriginal(feedback.getId())) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (feedback.getShareVersion() != expectedShareVersion) {
            throw new CustomException(ErrorCode.VERSION_CONFLICT);
        }
        if (guides.findBySharedCaseId(sharedCaseId).isEmpty()) {
            guides.save(new Guide(sharedCase, userRepository.getReferenceById(userId), Instant.now()));
        }
        return new GuideAddResponse(sharedCaseId, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void removeGuide(Long teamId, Long sharedCaseId, Long userId) {
        requireMembershipManager(teamId, userId);
        lockSharedCaseFeedback(teamId, sharedCaseId);
        sharedCases.findByIdAndTeamId(sharedCaseId, teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        guides.deleteBySharedCaseId(sharedCaseId);
    }

    private Feedback lockSharedCaseFeedback(Long teamId, Long sharedCaseId) {
        Long feedbackId = sharedCases.findFeedbackId(sharedCaseId, teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        // Sharing, guide assignment, and guide removal serialize on the same feedback row.
        return feedbackRepository.findByIdForUpdate(feedbackId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private TeamMember requireMembershipManager(Long teamId, Long userId) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!member.isLeader()) {
            throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
        }
        return member;
    }

    private PageRequest teamPageRequest(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_TEAM_PAGE_SIZE || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return PageRequest.of(page - 1, size);
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
