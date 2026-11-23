package com.babylion.team1.domain.dashboard.controller;

import static org.hamcrest.Matchers.hasSize;
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
import java.math.BigDecimal;
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
 * 대상 기간 2026-11-02(월) ~ 2026-11-15(일) = 14일, 온보딩 시작일 11-09, 표본 부족 기준 하루 3건(기본값).
 *
 *  - member1 / 업무A / gpt-4o      / 11-02 : 성공, 1000토큰, 호출비 0.5
 *  - member1 / 업무A / gpt-4o      / 11-02 : 실패, 500토큰, 호출비 0.25, has_rework, 수습 360초
 *  - member1 / 업무A / gpt-4o      / 11-02 : 성공, 1500토큰, 호출비 0.25
 *  - member2 / 업무A / gpt-4o      / 11-02 : 성공, 3000토큰, 호출비 1.0
 *  - member2 / 업무B / gpt-4o-mini / 11-04 : 실패, 800토큰, 호출비 0.1, has_rework
 *  - member1 / 업무A / gpt-4o      / 11-10 : 성공, 600토큰, 호출비 0.2
 *  - member1 / 업무A / gpt-4o      / 11-16 : 대상 기간 밖(집계 제외)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(DashboardFixture.class)
class TrendControllerTest {

    private static final LocalDate TARGET_FROM = LocalDate.of(2026, 11, 2);
    private static final LocalDate TARGET_TO = LocalDate.of(2026, 11, 15);
    private static final LocalDate ONBOARDING_START = LocalDate.of(2026, 11, 9);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private DashboardFixture fixture;

    private User leader;
    private User member1;
    private User outsider;
    private Team team;
    private TeamSettings settings;
    private TeamTaskType taskTypeA;

    @BeforeEach
    void setUp() {
        // 테스트도 로컬 DB를 쓰므로, 직접 가입해 둔 계정과 겹치지 않게 실행마다 고유한 이메일을 쓴다
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        leader = fixture.user("팀장", "trend-it-leader-" + suffix + "@test.com");
        member1 = fixture.user("팀원1", "trend-it-member1-" + suffix + "@test.com");
        User member2 = fixture.user("팀원2", "trend-it-member2-" + suffix + "@test.com");
        outsider = fixture.user("외부인", "trend-it-outsider-" + suffix + "@test.com");

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

        LocalDate nov2 = LocalDate.of(2026, 11, 2);
        saveConversation(upload1, member1, taskTypeA, "gpt-4o", nov2, true, false, 1000, "0.5", 0);
        saveConversation(upload1, member1, taskTypeA, "gpt-4o", nov2, false, true, 500, "0.25", 360);
        saveConversation(upload1, member1, taskTypeA, "gpt-4o", nov2, true, false, 1500, "0.25", 0);
        saveConversation(upload2, member2, taskTypeA, "gpt-4o", nov2, true, false, 3000, "1.0", 0);
        saveConversation(upload2, member2, taskTypeB, "gpt-4o-mini", LocalDate.of(2026, 11, 4),
                false, true, 800, "0.1", 0);
        saveConversation(upload1, member1, taskTypeA, "gpt-4o", LocalDate.of(2026, 11, 10),
                true, false, 600, "0.2", 0);
        saveConversation(upload1, member1, taskTypeA, "gpt-4o", LocalDate.of(2026, 11, 16),
                true, false, 9999, "9.0", 0);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("DAY 추이: 대상 기간의 모든 날을 오름차순으로 응답하고, 대화 없는 날은 value null이다")
    void dailyTrendForMember() throws Exception {
        perform(member1, url(team.getId()) + "?metric=tokensPerSuccess")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.metric").value("tokensPerSuccess"))
                .andExpect(jsonPath("$.data.estimated").value(true))
                .andExpect(jsonPath("$.data.onboardingStartDate").value("2026-11-09"))
                .andExpect(jsonPath("$.data.points", hasSize(14)))
                // 11-02: 3건, 성공 2, 토큰 3000 -> 표본 기준(3건) 충족
                .andExpect(jsonPath("$.data.points[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.points[0].value").value(1500.0))
                .andExpect(jsonPath("$.data.points[0].conversationCount").value(3))
                .andExpect(jsonPath("$.data.points[0].insufficientSample").value(false))
                // 11-03: 대화 없음
                .andExpect(jsonPath("$.data.points[1].date").value("2026-11-03"))
                .andExpect(jsonPath("$.data.points[1].value").isEmpty())
                .andExpect(jsonPath("$.data.points[1].conversationCount").value(0))
                .andExpect(jsonPath("$.data.points[1].insufficientSample").value(true))
                // 11-10: 1건 -> 표본 부족
                .andExpect(jsonPath("$.data.points[8].date").value("2026-11-10"))
                .andExpect(jsonPath("$.data.points[8].value").value(600.0))
                .andExpect(jsonPath("$.data.points[8].insufficientSample").value(true))
                .andExpect(jsonPath("$.data.points[13].date").value("2026-11-15"));
    }

    @Test
    @DisplayName("팀원은 본인 대화만, 팀장은 팀 전체 대화로 집계한다")
    void scopeDiffersByRole() throws Exception {
        perform(member1, url(team.getId()) + "?metric=conversationCount")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimated").value(false))
                .andExpect(jsonPath("$.data.points[0].value").value(3))
                .andExpect(jsonPath("$.data.points[2].value").value(0));

        perform(leader, url(team.getId()) + "?metric=conversationCount")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points[0].value").value(4))
                .andExpect(jsonPath("$.data.points[0].insufficientSample").value(false))
                .andExpect(jsonPath("$.data.points[2].value").value(1))
                .andExpect(jsonPath("$.data.points[8].value").value(1));
    }

    @Test
    @DisplayName("팀장이 scope=ME로 조회하면 본인 대화만, 팀원이 scope=TEAM이면 403 LEADER_ONLY")
    void scopeParameter() throws Exception {
        saveConversation(fixture.upload(team, leader), leader, taskTypeA, "gpt-4o", TARGET_FROM,
                true, false, 700, "0.1", 0);
        em.flush();
        em.clear();

        perform(leader, url(team.getId()) + "?metric=conversationCount&scope=ME")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points[0].value").value(1))
                .andExpect(jsonPath("$.data.points[2].value").value(0));

        perform(leader, url(team.getId()) + "?metric=conversationCount&scope=TEAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points[0].value").value(5));

        perform(member1, url(team.getId()) + "?metric=conversationCount&scope=TEAM")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_ONLY"));
    }

    @Test
    @DisplayName("costPerSuccess도 KPI 카드와 같은 계산식으로 날짜별 값을 낸다")
    void dailyCostPerSuccess() throws Exception {
        // 11-02 팀: 호출비 2.0 + 수습 360초 x 30000원/시간(3000) = 3002 / 성공 3건
        perform(leader, url(team.getId()) + "?metric=costPerSuccess")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimated").value(true))
                .andExpect(jsonPath("$.data.points[0].value").value(1000.67));
    }

    @Test
    @DisplayName("WEEK 추이: 월요일을 주 시작일로 묶고, 표본 기준은 하루 기준 x 포함 일수다")
    void weeklyTrend() throws Exception {
        perform(leader, url(team.getId()) + "?metric=successRate&unit=WEEK")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points", hasSize(2)))
                // 11-02 주: 5건, 성공 3 -> 기준 3 x 7일 = 21건보다 적음
                .andExpect(jsonPath("$.data.points[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.points[0].conversationCount").value(5))
                .andExpect(jsonPath("$.data.points[0].value").value(0.6))
                .andExpect(jsonPath("$.data.points[0].insufficientSample").value(true))
                .andExpect(jsonPath("$.data.points[1].date").value("2026-11-09"))
                .andExpect(jsonPath("$.data.points[1].conversationCount").value(1))
                .andExpect(jsonPath("$.data.points[1].value").value(1.0));
    }

    @Test
    @DisplayName("WEEK 추이에서 조회 시작일이 주 중간이면 그 주의 월요일 날짜로, 조회 기간 안의 대화만 묶는다")
    void weeklyTrendWithPartialWeek() throws Exception {
        perform(leader, url(team.getId()) + "?metric=conversationCount&unit=WEEK&from=2026-11-04&to=2026-11-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points", hasSize(2)))
                // 11-04 ~ 11-08만 포함 -> 11-02 대화 4건은 빠짐
                .andExpect(jsonPath("$.data.points[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.points[0].value").value(1))
                .andExpect(jsonPath("$.data.points[1].date").value("2026-11-09"))
                .andExpect(jsonPath("$.data.points[1].value").value(1));
    }

    @Test
    @DisplayName("taskTypes·models로 거르면 해당 대화만 집계한다")
    void filters() throws Exception {
        perform(leader, url(team.getId()) + "?metric=conversationCount&taskTypes=" + taskTypeA.getId()
                + "&models=gpt-4o")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points[0].value").value(4))
                .andExpect(jsonPath("$.data.points[2].value").value(0));
    }

    @Test
    @DisplayName("온보딩 시작일이 없으면 onboardingStartDate는 null이다")
    void onboardingStartDateNotSet() throws Exception {
        TeamSettings found = em.find(TeamSettings.class, settings.getId());
        found.updateTarget(TARGET_FROM, TARGET_TO, null, null);
        em.flush();
        em.clear();

        perform(member1, url(team.getId()) + "?metric=reworkRate")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStartDate").isEmpty())
                .andExpect(jsonPath("$.data.points", hasSize(14)));
    }

    @Test
    @DisplayName("metric이 없거나 잘못되면 400 INVALID_REQUEST로 응답한다")
    void invalidMetric() throws Exception {
        perform(member1, url(team.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        perform(member1, url(team.getId()) + "?metric=savingAmount")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("from > to, 잘못된 unit이면 400 INVALID_REQUEST로 응답한다")
    void invalidPeriodOrUnit() throws Exception {
        perform(member1, url(team.getId()) + "?metric=successRate&from=2026-11-10&to=2026-11-02")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        perform(member1, url(team.getId()) + "?metric=successRate&unit=MONTH")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("팀 멤버가 아니면 403 NOT_A_MEMBER로 응답한다")
    void notAMember() throws Exception {
        perform(outsider, url(team.getId()) + "?metric=successRate")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
    }

    @Test
    @DisplayName("API 문서에 metric이 필수 쿼리 파라미터로, 고를 수 있는 값과 함께 노출된다")
    void apiDocsExposeMetric() throws Exception {
        String metric = "$.paths['/api/v1/teams/{teamId}/dashboard/trends'].get.parameters[?(@.name == 'metric')]";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(metric + ".in").value("query"))
                .andExpect(jsonPath(metric + ".required").value(true))
                .andExpect(jsonPath(metric + ".schema.enum[0]").value("tokensPerSuccess"));
    }

    private ResultActions perform(User user, String url) throws Exception {
        return mockMvc.perform(fixture.get(user, url));
    }

    private String url(Long teamId) {
        return "/api/v1/teams/" + teamId + "/dashboard/trends";
    }

    private void saveConversation(Upload upload, User user, TeamTaskType taskType, String model, LocalDate date,
            boolean success, boolean rework, int tokens, String cost, int reworkSec) {
        fixture.save(fixture.conversation(upload, user, taskType, date)
                .model(model).estimatedTokens(tokens).estimatedCost(new BigDecimal(cost))
                .isSuccess(success).hasRework(rework).reworkTimeSec(reworkSec));
    }
}
