package com.babylion.team1.domain.dashboard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.dashboard.DashboardFixture;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대상 기간 2026-11-01 ~ 2026-11-20, 온보딩 시작일 2026-11-10.
 * 전: 11-01 ~ 11-09 / 후: 11-10 ~ 11-20
 *
 * 전 기간 대화
 *  - member1 / 업무A / 11-02 : 성공, 3000토큰, has_rework
 *  - member1 / 업무A / 11-05 : 실패, 1000토큰, has_rework
 *  - member2 / 업무A / 11-03 : 성공, 2000토큰
 * 후 기간 대화
 *  - member1 / 업무A / 11-12 : 성공, 1000토큰
 *  - member1 / 업무B / 11-20 : 성공, 800토큰
 *  - member2 / 업무A / 11-15 : 실패, 600토큰, has_rework
 * 대상 기간 밖(집계 제외)
 *  - member1 / 업무A / 10-31, 11-21
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(DashboardFixture.class)
class OnboardingEffectControllerTest {

    private static final LocalDate TARGET_FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TARGET_TO = LocalDate.of(2026, 11, 20);
    private static final LocalDate ONBOARDING_START = LocalDate.of(2026, 11, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private DashboardFixture fixture;

    private User leader;
    private User member1;
    private User member2;
    private User outsider;
    private Team team;
    private TeamSettings settings;
    private TeamTaskType taskTypeA;

    @BeforeEach
    void setUp() {
        // 테스트도 로컬 DB를 쓰므로, 직접 가입해 둔 계정과 겹치지 않게 실행마다 고유한 이메일을 쓴다
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        leader = fixture.user("팀장", "onboarding-it-leader-" + suffix + "@test.com");
        member1 = fixture.user("팀원1", "onboarding-it-member1-" + suffix + "@test.com");
        member2 = fixture.user("팀원2", "onboarding-it-member2-" + suffix + "@test.com");
        outsider = fixture.user("외부인", "onboarding-it-outsider-" + suffix + "@test.com");

        team = fixture.team("기획팀", leader);
        em.persist(TeamMember.leader(team, leader));
        em.persist(TeamMember.member(team, member1));
        em.persist(TeamMember.member(team, member2));

        settings = TeamSettings.createDefault(team);
        settings.updateTarget(TARGET_FROM, TARGET_TO, ONBOARDING_START, null);
        em.persist(settings);

        taskTypeA = fixture.taskType(team, "주간 보고서 작성", "수정 없이 제출");
        TeamTaskType taskTypeB = fixture.taskType(team, "회의록 정리", "바로 공유");

        Upload upload1 = fixture.upload(team, member1);
        Upload upload2 = fixture.upload(team, member2);

        // 전
        saveConversation(upload1, member1, taskTypeA, LocalDate.of(2026, 11, 2), true, true, 3000);
        saveConversation(upload1, member1, taskTypeA, LocalDate.of(2026, 11, 5), false, true, 1000);
        saveConversation(upload2, member2, taskTypeA, LocalDate.of(2026, 11, 3), true, false, 2000);
        // 후
        saveConversation(upload1, member1, taskTypeA, LocalDate.of(2026, 11, 12), true, false, 1000);
        saveConversation(upload1, member1, taskTypeB, LocalDate.of(2026, 11, 20), true, false, 800);
        saveConversation(upload2, member2, taskTypeA, LocalDate.of(2026, 11, 15), false, true, 600);
        // 대상 기간 밖
        saveConversation(upload1, member1, taskTypeA, LocalDate.of(2026, 10, 31), true, false, 9999);
        saveConversation(upload1, member1, taskTypeA, LocalDate.of(2026, 11, 21), true, false, 9999);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("팀원이 조회하면 본인 대화로 온보딩 전/후 핵심 지표를 응답한다")
    void memberGetsOwnValues() throws Exception {
        perform(member1, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.onboardingStartDate").value("2026-11-10"))
                // 전: 2건, 성공 1, 토큰 4000, 재작업 2
                .andExpect(jsonPath("$.data.before.conversationCount").value(2))
                .andExpect(jsonPath("$.data.before.tokensPerSuccess").value(4000.0))
                .andExpect(jsonPath("$.data.before.reworkRate").value(1.0))
                .andExpect(jsonPath("$.data.before.successRate").value(0.5))
                // 후: 2건, 성공 2, 토큰 1800, 재작업 0
                .andExpect(jsonPath("$.data.after.conversationCount").value(2))
                .andExpect(jsonPath("$.data.after.tokensPerSuccess").value(900.0))
                .andExpect(jsonPath("$.data.after.reworkRate").value(0.0))
                .andExpect(jsonPath("$.data.after.successRate").value(1.0))
                .andExpect(jsonPath("$.data.qualityWarning").value(false));
    }

    @Test
    @DisplayName("팀장이 조회하면 팀 집계를 응답하고, 성공률이 유지되면 qualityWarning은 false다")
    void leaderGetsTeamAggregate() throws Exception {
        perform(leader, url(team.getId()))
                .andExpect(status().isOk())
                // 전: 3건, 성공 2, 토큰 6000
                .andExpect(jsonPath("$.data.before.conversationCount").value(3))
                .andExpect(jsonPath("$.data.before.tokensPerSuccess").value(3000.0))
                .andExpect(jsonPath("$.data.before.reworkRate").value(0.6667))
                .andExpect(jsonPath("$.data.before.successRate").value(0.6667))
                // 후: 3건, 성공 2, 토큰 2400 -> 토큰은 줄었지만 성공률은 같음
                .andExpect(jsonPath("$.data.after.conversationCount").value(3))
                .andExpect(jsonPath("$.data.after.tokensPerSuccess").value(1200.0))
                .andExpect(jsonPath("$.data.after.reworkRate").value(0.3333))
                .andExpect(jsonPath("$.data.after.successRate").value(0.6667))
                .andExpect(jsonPath("$.data.qualityWarning").value(false));
    }

    @Test
    @DisplayName("팀장이 scope=ME로 조회하면 본인 대화만, 팀원이 scope=TEAM이면 403 LEADER_ONLY")
    void scopeParameter() throws Exception {
        saveConversation(fixture.upload(team, leader), leader, taskTypeA, LocalDate.of(2026, 11, 12), true, false, 500);
        em.flush();
        em.clear();

        perform(leader, url(team.getId()) + "?scope=ME")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.before.conversationCount").value(0))
                .andExpect(jsonPath("$.data.after.conversationCount").value(1))
                .andExpect(jsonPath("$.data.after.tokensPerSuccess").value(500.0));

        perform(leader, url(team.getId()) + "?scope=TEAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.before.conversationCount").value(3))
                .andExpect(jsonPath("$.data.after.conversationCount").value(4));

        perform(member1, url(team.getId()) + "?scope=TEAM")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_ONLY"));
    }

    @Test
    @DisplayName("성공률이 떨어졌는데 토큰만 줄었으면 qualityWarning은 true다 (taskTypes 필터 적용)")
    void qualityWarningWhenSuccessRateDropsAndTokensDecrease() throws Exception {
        perform(leader, url(team.getId()) + "?taskTypes=" + taskTypeA.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.before.tokensPerSuccess").value(3000.0))
                .andExpect(jsonPath("$.data.before.successRate").value(0.6667))
                // 후(업무A만): 2건, 성공 1, 토큰 1600
                .andExpect(jsonPath("$.data.after.conversationCount").value(2))
                .andExpect(jsonPath("$.data.after.tokensPerSuccess").value(1600.0))
                .andExpect(jsonPath("$.data.after.successRate").value(0.5))
                .andExpect(jsonPath("$.data.qualityWarning").value(true));
    }

    @Test
    @DisplayName("온보딩 시작일이 없으면 before·after는 null, qualityWarning은 false다")
    void onboardingStartDateNotSet() throws Exception {
        updateSettings(TARGET_FROM, TARGET_TO, null);

        perform(member1, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStartDate").isEmpty())
                .andExpect(jsonPath("$.data.before").isEmpty())
                .andExpect(jsonPath("$.data.after").isEmpty())
                .andExpect(jsonPath("$.data.qualityWarning").value(false));
    }

    @Test
    @DisplayName("대상 기간이 없으면 기간을 나눌 수 없어 before·after는 null이다")
    void targetPeriodNotSet() throws Exception {
        updateSettings(null, null, ONBOARDING_START);

        perform(leader, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStartDate").value("2026-11-10"))
                .andExpect(jsonPath("$.data.before").isEmpty())
                .andExpect(jsonPath("$.data.after").isEmpty())
                .andExpect(jsonPath("$.data.qualityWarning").value(false));
    }

    @Test
    @DisplayName("한쪽 기간에 대화가 없으면 그쪽 지표는 null이고 qualityWarning은 false다")
    void oneSideHasNoConversations() throws Exception {
        // 온보딩 시작일 = 대상 기간 시작일 -> 전 기간이 비어 있음
        updateSettings(TARGET_FROM, TARGET_TO, TARGET_FROM);

        perform(leader, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.before.conversationCount").value(0))
                .andExpect(jsonPath("$.data.before.tokensPerSuccess").isEmpty())
                .andExpect(jsonPath("$.data.before.reworkRate").isEmpty())
                .andExpect(jsonPath("$.data.before.successRate").isEmpty())
                .andExpect(jsonPath("$.data.after.conversationCount").value(6))
                .andExpect(jsonPath("$.data.qualityWarning").value(false));
    }

    @Test
    @DisplayName("후 기간에 성공이 0건이면 tokensPerSuccess는 null이고 qualityWarning은 false다")
    void noSuccessAfterOnboarding() throws Exception {
        // member2 후: 실패 1건 -> 성공률은 떨어졌지만 토큰 비교 불가
        perform(member2, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.before.successRate").value(1.0))
                .andExpect(jsonPath("$.data.after.conversationCount").value(1))
                .andExpect(jsonPath("$.data.after.successRate").value(0.0))
                .andExpect(jsonPath("$.data.after.tokensPerSuccess").isEmpty())
                .andExpect(jsonPath("$.data.qualityWarning").value(false));
    }

    @Test
    @DisplayName("팀 멤버가 아니면 403 NOT_A_MEMBER, 없는 팀이면 404 TEAM_NOT_FOUND로 응답한다")
    void accessDenied() throws Exception {
        perform(outsider, url(team.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));

        perform(leader, url(team.getId() + 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("taskTypes 형식이 잘못되면 400 INVALID_REQUEST로 응답한다")
    void invalidTaskTypes() throws Exception {
        perform(member1, url(team.getId()) + "?taskTypes=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("API 문서에 taskTypes가 쿼리 파라미터로 노출된다")
    void apiDocsExposeTaskTypes() throws Exception {
        String params = "$.paths['/api/v1/teams/{teamId}/dashboard/onboarding-effect'].get.parameters";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(params + "[?(@.name == 'taskTypes')].in").value("query"))
                .andExpect(jsonPath(params + "[?(@.name == 'request')]").isEmpty());
    }

    private void updateSettings(LocalDate targetFrom, LocalDate targetTo, LocalDate onboardingStartDate) {
        TeamSettings found = em.find(TeamSettings.class, settings.getId());
        found.updateTarget(targetFrom, targetTo, onboardingStartDate, null);
        em.flush();
        em.clear();
    }

    private ResultActions perform(User user, String url) throws Exception {
        return mockMvc.perform(fixture.get(user, url));
    }

    private String url(Long teamId) {
        return "/api/v1/teams/" + teamId + "/dashboard/onboarding-effect";
    }

    private void saveConversation(Upload upload, User user, TeamTaskType taskType, LocalDate date,
            boolean success, boolean rework, int tokens) {
        fixture.save(fixture.conversation(upload, user, taskType, date)
                .estimatedTokens(tokens).isSuccess(success).hasRework(rework));
    }
}
