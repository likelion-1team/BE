package com.babylion.team1.domain.team.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.support.TestAccessTokens;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.Instant;
import com.babylion.team1.domain.analysis.entity.Analysis;
import com.babylion.team1.domain.analysis.entity.AnalysisItem;
import com.babylion.team1.domain.analysis.entity.AnalysisItemStatus;
import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamMemberListControllerIntegrationTest {

    private static final String PATH = "/api/v1/teams/{teamId}/members";
    private static final LocalDate FROM = LocalDate.of(2025, 8, 1);
    private static final LocalDate TO = LocalDate.of(2025, 8, 31);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TestAccessTokens accessTokens;

    @Test
    void includesLeaderAndCompletedConversationsOnBothDateBoundaries() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        configurePeriod(fixture.team());
        User member = addMember(fixture, "팀원");
        createTeam(saveUser("다른 팀장"));

        saveConversation(fixture, fixture.leader(), FROM, UploadStatus.DONE);
        saveConversation(fixture, member, TO, UploadStatus.DONE);
        saveConversation(fixture, member, TO.minusDays(1), UploadStatus.DONE);

        listMembers(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andExpect(jsonPath("$.timestamp", endsWith("+09:00")))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(fixture.leader().getId()))
                .andExpect(jsonPath("$.data[0].name").value(fixture.leader().getName()))
                .andExpect(jsonPath("$.data[0].email").value(fixture.leader().getEmail()))
                .andExpect(jsonPath("$.data[0].role").value("LEADER"))
                .andExpect(jsonPath("$.data[0].submitted").value(true))
                .andExpect(jsonPath("$.data[1].userId").value(member.getId()))
                .andExpect(jsonPath("$.data[1].name").value(member.getName()))
                .andExpect(jsonPath("$.data[1].email").value(member.getEmail()))
                .andExpect(jsonPath("$.data[1].role").value("MEMBER"))
                .andExpect(jsonPath("$.data[1].submitted").value(true))
                .andExpect(jsonPath("$.data[1].conversationCount").doesNotExist());
    }

    @Test
    void excludesConversationsOutsideThePeriodAndInAnotherTeam() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        configurePeriod(fixture.team());
        User member = addMember(fixture, "두 팀에 속한 사용자");
        TeamFixture other = createTeam(member);

        saveConversation(fixture, member, FROM.minusDays(1), UploadStatus.DONE);
        saveConversation(fixture, member, TO.plusDays(1), UploadStatus.DONE);
        saveConversation(other, member, FROM, UploadStatus.DONE);

        listMembers(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].submitted").value(false))
                .andExpect(jsonPath("$.data[1].userId").value(member.getId()))
                .andExpect(jsonPath("$.data[1].submitted").value(false));
    }

    @ParameterizedTest
    @EnumSource(value = UploadStatus.class, names = {"UPLOADED", "ANALYZING", "FAILED"})
    void excludesConversationRowsFromUnfinishedUploads(UploadStatus uploadStatus) throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        configurePeriod(fixture.team());
        User member = addMember(fixture, "팀원");
        saveConversation(fixture, member, FROM, uploadStatus);

        listMembers(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].submitted").value(false));
    }

    @Test
    void retainsMembershipsWithFalseSubmissionWhenPeriodIsMissing() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        User member = addMember(fixture, "팀원");
        saveConversation(fixture, member, FROM, UploadStatus.DONE);

        listMembers(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].submitted").value(false));

        TeamSettings settings = TeamSettings.createDefault(fixture.team());
        entityManager.persist(settings);
        listMembers(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].submitted").value(false));
    }

    @Test
    void enforcesLeadershipInRequestedTeamAndReportsMissingTeam() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        User otherLeader = saveUser("다른 팀장");
        createTeam(otherLeader);

        listMembers(fixture.team().getId(), otherLeader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
        entityManager.persist(TeamMember.member(fixture.team(), otherLeader));
        listMembers(fixture.team().getId(), otherLeader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_ONLY"));
        listMembers(Long.MAX_VALUE, otherLeader)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    void requiresAccessTokenAndExposesGetOperationAlongsidePost() throws Exception {
        mockMvc.perform(get(PATH, 1L)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(get(PATH, 1L).header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/members'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/members'].post.responses['201']").exists());
    }

    @Test
    void summarizesCompletedSubmissionsForTheTargetPeriod() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        User member = addMember(fixture, "제출한 팀원");
        User pending = addMember(fixture, "미제출 팀원");
        TeamFixture other = createTeam(pending);
        TeamSettings settings = TeamSettings.createDefault(fixture.team());
        settings.updateTarget(FROM, TO, FROM.plusDays(10), 120);
        entityManager.persist(settings);

        saveConversation(fixture, fixture.leader(), FROM, UploadStatus.DONE);
        saveConversation(fixture, member, TO, UploadStatus.DONE);
        saveConversation(fixture, member, TO.minusDays(1), UploadStatus.DONE);
        saveConversation(fixture, pending, FROM.minusDays(1), UploadStatus.DONE);
        saveConversation(fixture, pending, TO.plusDays(1), UploadStatus.DONE);
        saveConversation(fixture, pending, FROM, UploadStatus.UPLOADED);
        saveConversation(fixture, pending, FROM, UploadStatus.ANALYZING);
        saveConversation(fixture, pending, FROM, UploadStatus.FAILED);
        saveConversation(other, pending, FROM, UploadStatus.DONE);

        submissions(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data.submittedMembers").value(2))
                .andExpect(jsonPath("$.data.totalMembers").value(3))
                .andExpect(jsonPath("$.data.analyzedConversations").value(nullValue()))
                .andExpect(jsonPath("$.data.targetCount").value(nullValue()))
                .andExpect(jsonPath("$.data.coverageRatio").value(nullValue()))
                .andExpect(jsonPath("$.data.status").value("SUPPRESSED"));
    }

    @Test
    void returnsZeroSubmissionsAndNullTargetWhenPeriodIsMissing() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        addMember(fixture, "팀원");
        saveConversation(fixture, fixture.leader(), FROM, UploadStatus.DONE);

        submissions(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedMembers").value(0))
                .andExpect(jsonPath("$.data.totalMembers").value(2))
                .andExpect(jsonPath("$.data.analyzedConversations").value(nullValue()))
                .andExpect(jsonPath("$.data.targetCount").value(nullValue()));
    }

    @Test
    void restrictsSubmissionsToTheRequestedTeamLeader() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        User member = addMember(fixture, "팀원");
        User outsider = saveUser("다른 팀장");
        createTeam(outsider);

        submissions(fixture.team().getId(), member)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_FORBIDDEN"));
        submissions(fixture.team().getId(), outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        submissions(Long.MAX_VALUE, fixture.leader())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/teams/{teamId}/submissions", fixture.team().getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsEachMemberOnceUsingOnlyTheCurrentSettings() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        User member = addMember(fixture, "이전 기간에만 제출한 팀원");
        TeamSettings initial = saveInitialSettings(fixture.team());
        TeamSettings previous = saveNextSettings(initial, FROM, TO, null);
        saveNextSettings(previous, FROM.plusMonths(1), TO.plusMonths(1), null);

        saveConversation(fixture, fixture.leader(), FROM.plusMonths(1), UploadStatus.DONE);
        saveConversation(fixture, member, FROM, UploadStatus.DONE);

        listMembers(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(fixture.leader().getId()))
                .andExpect(jsonPath("$.data[0].submitted").value(true))
                .andExpect(jsonPath("$.data[1].userId").value(member.getId()))
                .andExpect(jsonPath("$.data[1].submitted").value(false));
    }

    @Test
    void summarizesSubmissionsWithTheLatestTargetCount() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        addMember(fixture, "미제출 팀원");
        TeamSettings initial = saveInitialSettings(fixture.team());
        TeamSettings previous = saveNextSettings(initial, FROM, TO, 120);
        saveNextSettings(previous, FROM, TO, 200);

        saveConversation(fixture, fixture.leader(), FROM, UploadStatus.DONE);

        for (int index = 0; index < 4; index++) {
            saveConversation(fixture, addMember(fixture, "기여자 " + index), FROM, UploadStatus.DONE);
        }

        submissions(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedMembers").value(5))
                .andExpect(jsonPath("$.data.totalMembers").value(6))
                .andExpect(jsonPath("$.data.analyzedConversations").value(5))
                .andExpect(jsonPath("$.data.targetCount").value(200))
                .andExpect(jsonPath("$.data.settingsVersion").value(2))
                .andExpect(jsonPath("$.data.coverageRatio").value(0.025))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    void countsSubmissionsOnceWhenSettingsPeriodsOverlap() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        User member = addMember(fixture, "제출한 팀원");
        TeamSettings initial = saveInitialSettings(fixture.team());
        TeamSettings previous = saveNextSettings(initial, FROM, TO, null);
        saveNextSettings(previous, FROM.plusDays(5), TO, null);

        saveConversation(fixture, fixture.leader(), FROM, UploadStatus.DONE);
        saveConversation(fixture, member, TO, UploadStatus.DONE);
        saveConversation(fixture, member, TO.minusDays(1), UploadStatus.DONE);

        submissions(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedMembers").value(1))
                .andExpect(jsonPath("$.data.totalMembers").value(2))
                .andExpect(jsonPath("$.data.analyzedConversations").value(nullValue()))
                .andExpect(jsonPath("$.data.targetCount").value(nullValue()));
    }

    @Test
    void excludesSubmissionsThatOnlyMatchAPreviousPeriod() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        User member = addMember(fixture, "이전 기간에만 제출한 팀원");
        TeamSettings initial = saveInitialSettings(fixture.team());
        TeamSettings previous = saveNextSettings(initial, FROM, TO, null);
        saveNextSettings(previous, FROM.plusMonths(1), TO.plusMonths(1), null);

        saveConversation(fixture, member, FROM, UploadStatus.DONE);

        submissions(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedMembers").value(0))
                .andExpect(jsonPath("$.data.totalMembers").value(2))
                .andExpect(jsonPath("$.data.analyzedConversations").value(nullValue()));
    }

    @Test
    void countsOnlySuccessfulCurrentAnalysisItemsFromConsumedUploads() throws Exception {
        TeamFixture fixture = createTeam(saveUser("팀장"));
        User member = addMember(fixture, "성공 팀원");
        User pending = addMember(fixture, "미제출 팀원");
        TeamSettings previous = saveNextSettings(saveInitialSettings(fixture.team()), FROM, TO, 100);
        TeamSettings current = saveNextSettings(previous, FROM, TO, 200);

        saveAnalysisItem(fixture, member, current, AnalysisItemStatus.SUCCEEDED, true, false);
        saveAnalysisItem(fixture, member, current, AnalysisItemStatus.SUCCEEDED, true, false);
        saveAnalysisItem(fixture, pending, previous, AnalysisItemStatus.SUCCEEDED, true, false);
        saveAnalysisItem(fixture, pending, current, AnalysisItemStatus.QUEUED, false, false);
        saveAnalysisItem(fixture, pending, current, AnalysisItemStatus.FAILED, false, false);
        saveAnalysisItem(fixture, pending, current, AnalysisItemStatus.SUCCEEDED, false, false);
        saveAnalysisItem(fixture, pending, current, AnalysisItemStatus.SUCCEEDED, true, true);

        listMembers(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].submitted").value(false))
                .andExpect(jsonPath("$.data[1].submitted").value(true))
                .andExpect(jsonPath("$.data[2].submitted").value(false));
        submissions(fixture.team().getId(), fixture.leader()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedMembers").value(2))
                .andExpect(jsonPath("$.data.totalMembers").value(3))
                .andExpect(jsonPath("$.data.analyzedConversations").value(nullValue()))
                .andExpect(jsonPath("$.data.targetCount").value(nullValue()));

        for (int index = 0; index < 4; index++) {
            saveAnalysisItem(fixture, addMember(fixture, "공개 기준 기여자 " + index), current,
                    AnalysisItemStatus.SUCCEEDED, true, false);
        }
        submissions(fixture.team().getId(), fixture.leader()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedMembers").value(6))
                .andExpect(jsonPath("$.data.totalMembers").value(7))
                .andExpect(jsonPath("$.data.analyzedConversations").value(6))
                .andExpect(jsonPath("$.data.targetCount").value(200))
                .andExpect(jsonPath("$.data.coverageRatio").value(0.03))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    private void saveAnalysisItem(TeamFixture fixture, User user, TeamSettings settings, AnalysisItemStatus status,
                                  boolean current, boolean deleted) {
        Upload upload = Upload.builder().team(fixture.team()).uploader(user).settings(settings)
                .fileName("conversations.json").conversationCount(1).build();
        upload.consume();
        entityManager.persist(upload);
        Conversation conversation = Conversation.builder().team(fixture.team()).user(user).upload(upload)
                .taskType(fixture.type()).conversationDate(FROM).startedAt(FROM.atStartOfDay())
                .externalId(UUID.randomUUID().toString()).turnCount(1).estimatedTokens(100).isSuccess(false).build();
        entityManager.persist(conversation);
        Instant started = Instant.parse("2026-11-01T00:00:00Z");
        var version = ConversationVersion.builder().conversation(conversation).sourceUpload(upload)
                .inputVersion(1).contentHash("a".repeat(64)).startedAt(started).conversationDate(FROM).turnCount(1)
                .deletedAt(deleted ? started : null).build();
        entityManager.persist(version);
        var analysis = Analysis.builder().upload(upload).team(fixture.team()).user(user).settings(settings)
                .idempotencyKey(UUID.randomUUID().toString()).requestHash("b".repeat(64)).selectedCount(1).build();
        entityManager.persist(analysis);
        entityManager.persist(AnalysisItem.builder().analysis(analysis).version(version).team(fixture.team()).user(user)
                .taskType(fixture.type()).conversationDate(FROM).status(status).current(current).build());
    }

    private ResultActions submissions(Long teamId, User user) throws Exception {
        return mockMvc.perform(get("/api/v1/teams/{teamId}/submissions", teamId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(user.getId())));
    }

    private TeamFixture createTeam(User leader) {
        Team team = Team.builder().name("팀원 목록 검증").createdBy(leader).build();
        entityManager.persist(team);
        entityManager.persist(TeamMember.leader(team, leader));
        TeamTaskType type = TeamTaskType.builder().team(team).name("업무 보고서").successConditions(List.of("검토 통과"))
                .failureConditions(List.of()).unknownConditions(List.of()).build();
        entityManager.persist(type);
        return new TeamFixture(team, leader, type);
    }

    private User addMember(TeamFixture fixture, String name) {
        User member = saveUser(name);
        entityManager.persist(TeamMember.member(fixture.team(), member));
        return member;
    }

    private User saveUser(String name) {
        User user = User.builder().name(name)
                .email("team-roster-it-" + UUID.randomUUID() + "@example.com").passwordHash("test-password-hash").build();
        entityManager.persist(user);
        return user;
    }

    private void configurePeriod(Team team) {
        TeamSettings settings = TeamSettings.createDefault(team);
        settings.updateTarget(FROM, TO, FROM.plusDays(10), null);
        entityManager.persist(settings);
    }

    /** 팀 생성 시 저장되는 기간 미설정 상태의 첫 버전 */
    private TeamSettings saveInitialSettings(Team team) {
        TeamSettings settings = TeamSettings.createDefault(team);
        entityManager.persist(settings);
        return settings;
    }

    /** 기준을 다시 저장한 상태. 이전 버전은 현재 기준에서 내려간다 */
    private TeamSettings saveNextSettings(TeamSettings previous, LocalDate from, LocalDate to, Integer targetCount) {
        TeamSettings next = previous.nextVersion(null, from, to, from, targetCount, null);
        entityManager.persist(next);
        return next;
    }

    private void saveConversation(TeamFixture fixture, User user, LocalDate date, UploadStatus uploadStatus) {
        Upload upload = Upload.builder().team(fixture.team()).uploader(user)
                .fileName("conversations.json").conversationCount(1).build();
        if (uploadStatus != UploadStatus.UPLOADED) {
            upload.startAnalysis(1);
        }
        if (uploadStatus == UploadStatus.DONE) {
            upload.complete();
        } else if (uploadStatus == UploadStatus.FAILED) {
            upload.fail("테스트 실패");
        }
        entityManager.persist(upload);
        entityManager.persist(Conversation.builder().team(fixture.team()).user(user).upload(upload).taskType(fixture.type())
                .conversationDate(date).startedAt(date.atStartOfDay()).externalId(UUID.randomUUID().toString())
                .turnCount(1).estimatedTokens(100).isSuccess(false).build());
    }

    private ResultActions listMembers(Long teamId, User user) throws Exception {
        return mockMvc.perform(get(PATH, teamId).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + accessTokens.create(user.getId())));
    }

    private record TeamFixture(Team team, User leader, TeamTaskType type) {}
}
