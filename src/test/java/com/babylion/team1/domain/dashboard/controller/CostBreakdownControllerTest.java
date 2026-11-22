package com.babylion.team1.domain.dashboard.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.jwt.JwtProvider;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대상 기간 2026-11-02(월) ~ 2026-11-15(일) = 14일, 시간당 인건비 30000원(기본값) -> 120초 = 1000원.
 *
 *  - member1 / 주간 보고서 작성 / gpt-4o     / 11-02 : 호출비 100,   검토 120초(1000원)
 *  - member1 / 회의록 정리      / o3(단가 없음) / 11-02 : 호출비 NULL,  수습 240초(2000원)
 *  - member2 / 주간 보고서 작성 / gpt-4o     / 11-02 : 호출비 50.4
 *  - member2 / 주간 보고서 작성 / model NULL / 11-04 : 호출비 20.6,  검토 60초(500원)
 *  - member1 / 주간 보고서 작성 / gpt-4o     / 11-10 : 호출비 30,    수습 120초(1000원)
 *  - member1 / 주간 보고서 작성 / gpt-4o     / 11-16 : 대상 기간 밖(집계 제외)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CostBreakdownControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private JwtProvider jwtProvider;

    private User leader;
    private User member1;
    private User outsider;
    private Team team;
    private int externalIdSeq;

    @BeforeEach
    void setUp() {
        // 테스트도 로컬 DB를 쓰므로, 직접 가입해 둔 계정과 겹치지 않게 실행마다 고유한 이메일을 쓴다
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        leader = saveUser("팀장", "cost-it-leader-" + suffix + "@test.com");
        member1 = saveUser("팀원1", "cost-it-member1-" + suffix + "@test.com");
        User member2 = saveUser("팀원2", "cost-it-member2-" + suffix + "@test.com");
        outsider = saveUser("외부인", "cost-it-outsider-" + suffix + "@test.com");

        team = Team.builder().name("기획팀").description("설명").createdBy(leader).build();
        em.persist(team);
        em.persist(TeamMember.leader(team, leader));
        em.persist(TeamMember.member(team, member1));
        em.persist(TeamMember.member(team, member2));

        TeamSettings settings = TeamSettings.createDefault(team);
        settings.updateTarget(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 15), null, null);
        em.persist(settings);

        TeamTaskType report = saveTaskType("주간 보고서 작성");
        TeamTaskType minutes = saveTaskType("회의록 정리");

        Upload upload1 = saveUpload(member1);
        Upload upload2 = saveUpload(member2);

        LocalDate nov2 = LocalDate.of(2026, 11, 2);
        saveConversation(upload1, member1, report, "gpt-4o", nov2, "100.0000", 120, 0);
        saveConversation(upload1, member1, minutes, "o3", nov2, null, 0, 240);
        saveConversation(upload2, member2, report, "gpt-4o", nov2, "50.4000", 0, 0);
        saveConversation(upload2, member2, report, null, LocalDate.of(2026, 11, 4), "20.6000", 60, 0);
        saveConversation(upload1, member1, report, "gpt-4o", LocalDate.of(2026, 11, 10), "30.0000", 0, 120);
        saveConversation(upload1, member1, report, "gpt-4o", LocalDate.of(2026, 11, 16), "999.0000", 0, 0);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("COST_TYPE·DAY: 날짜마다 CALL·REVIEW·REWORK 3개를 원 단위로 반올림해 응답하고, 대화 없는 날은 0이다")
    void costTypeDaily() throws Exception {
        perform(leader, url(team.getId()) + "?groupBy=COST_TYPE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.groupBy").value("COST_TYPE"))
                .andExpect(jsonPath("$.data.estimated").value(true))
                .andExpect(jsonPath("$.data.points", hasSize(14)))
                // 11-02: 호출비 100 + 0(단가 없음) + 50.4 = 150.4 -> 150
                .andExpect(jsonPath("$.data.points[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.points[0].items[*].key", contains("CALL", "REVIEW", "REWORK")))
                .andExpect(jsonPath("$.data.points[0].items[*].cost", contains(150, 1000, 2000)))
                // 11-03: 대화 없음
                .andExpect(jsonPath("$.data.points[1].date").value("2026-11-03"))
                .andExpect(jsonPath("$.data.points[1].items[*].cost", contains(0, 0, 0)))
                // 11-04: 호출비 20.6 -> 21
                .andExpect(jsonPath("$.data.points[2].items[*].cost", contains(21, 500, 0)))
                .andExpect(jsonPath("$.data.points[8].date").value("2026-11-10"))
                .andExpect(jsonPath("$.data.points[8].items[*].cost", contains(30, 0, 1000)))
                .andExpect(jsonPath("$.data.points[13].date").value("2026-11-15"));
    }

    @Test
    @DisplayName("팀원은 본인 대화만, 팀장은 팀 전체 대화로 집계한다")
    void scopeDiffersByRole() throws Exception {
        perform(member1, url(team.getId()) + "?groupBy=COST_TYPE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points[0].items[*].cost", contains(100, 1000, 2000)))
                .andExpect(jsonPath("$.data.points[2].items[*].cost", contains(0, 0, 0)));
    }

    @Test
    @DisplayName("MODEL·DAY: 모델별 총비용(호출+검토+수습)을 이름순으로 응답하고, 모델이 없으면 UNKNOWN이다")
    void modelDaily() throws Exception {
        perform(leader, url(team.getId()) + "?groupBy=MODEL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupBy").value("MODEL"))
                // 11-02: gpt-4o 100 + 1000 + 50.4 = 1150.4 -> 1150, o3 0 + 2000
                .andExpect(jsonPath("$.data.points[0].items[*].key", contains("gpt-4o", "o3")))
                .andExpect(jsonPath("$.data.points[0].items[*].cost", contains(1150, 2000)))
                // 대화 없는 날은 빈 목록
                .andExpect(jsonPath("$.data.points[1].items", hasSize(0)))
                // 11-04: 20.6 + 500 = 520.6 -> 521
                .andExpect(jsonPath("$.data.points[2].items[*].key", contains("UNKNOWN")))
                .andExpect(jsonPath("$.data.points[2].items[*].cost", contains(521)));
    }

    @Test
    @DisplayName("MODEL·WEEK: 월요일 시작 주로 묶고, UNKNOWN은 다른 모델 뒤에 둔다")
    void modelWeekly() throws Exception {
        perform(leader, url(team.getId()) + "?groupBy=MODEL&unit=WEEK")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points", hasSize(2)))
                .andExpect(jsonPath("$.data.points[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.points[0].items[*].key", contains("gpt-4o", "o3", "UNKNOWN")))
                .andExpect(jsonPath("$.data.points[0].items[*].cost", contains(1150, 2000, 521)))
                .andExpect(jsonPath("$.data.points[1].date").value("2026-11-09"))
                .andExpect(jsonPath("$.data.points[1].items[*].key", contains("gpt-4o")))
                .andExpect(jsonPath("$.data.points[1].items[*].cost", contains(1030)));
    }

    @Test
    @DisplayName("TASK_TYPE·WEEK: 업무 유형명별 총비용을 반올림 전 값으로 합산한 뒤 원 단위로 응답한다")
    void taskTypeWeekly() throws Exception {
        perform(leader, url(team.getId()) + "?groupBy=TASK_TYPE&unit=WEEK")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupBy").value("TASK_TYPE"))
                // 주간 보고서 작성: 1100 + 50.4 + 520.6 = 1671
                .andExpect(jsonPath("$.data.points[0].items[*].key", contains("주간 보고서 작성", "회의록 정리")))
                .andExpect(jsonPath("$.data.points[0].items[*].cost", contains(1671, 2000)))
                .andExpect(jsonPath("$.data.points[1].items[*].key", contains("주간 보고서 작성")))
                .andExpect(jsonPath("$.data.points[1].items[*].cost", contains(1030)));
    }

    @Test
    @DisplayName("단가 없는 모델(estimated_cost NULL)의 호출비는 0으로 계산한다")
    void modelWithoutPrice() throws Exception {
        perform(leader, url(team.getId()) + "?groupBy=COST_TYPE&models=o3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points[0].items[*].cost", contains(0, 0, 2000)));
    }

    @Test
    @DisplayName("groupBy가 없거나 잘못되면 400 INVALID_REQUEST로 응답한다")
    void invalidGroupBy() throws Exception {
        perform(member1, url(team.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        perform(member1, url(team.getId()) + "?groupBy=USER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("from > to, 잘못된 unit이면 400 INVALID_REQUEST로 응답한다")
    void invalidPeriodOrUnit() throws Exception {
        perform(member1, url(team.getId()) + "?groupBy=MODEL&from=2026-11-10&to=2026-11-02")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        perform(member1, url(team.getId()) + "?groupBy=MODEL&unit=MONTH")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("팀 멤버가 아니면 403 NOT_A_MEMBER로 응답한다")
    void notAMember() throws Exception {
        perform(outsider, url(team.getId()) + "?groupBy=COST_TYPE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
    }

    @Test
    @DisplayName("API 문서에 groupBy가 필수 쿼리 파라미터로, 고를 수 있는 값과 함께 노출된다")
    void apiDocsExposeGroupBy() throws Exception {
        String groupBy = "$.paths['/api/v1/teams/{teamId}/dashboard/cost-breakdown'].get.parameters"
                + "[?(@.name == 'groupBy')]";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(groupBy + ".in").value("query"))
                .andExpect(jsonPath(groupBy + ".required").value(true))
                .andExpect(jsonPath(groupBy + ".schema.enum[0]").value("COST_TYPE"));
    }

    private ResultActions perform(User user, String url) throws Exception {
        return mockMvc.perform(get(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId())));
    }

    private String url(Long teamId) {
        return "/api/v1/teams/" + teamId + "/dashboard/cost-breakdown";
    }

    private User saveUser(String name, String email) {
        User user = User.builder().name(name).email(email).passwordHash("hashed-password").build();
        em.persist(user);
        return user;
    }

    private TeamTaskType saveTaskType(String name) {
        TeamTaskType taskType = TeamTaskType.builder().team(team).name(name).successDefinition("성공 정의").build();
        em.persist(taskType);
        return taskType;
    }

    private Upload saveUpload(User uploader) {
        Upload upload = Upload.builder().team(team).uploader(uploader).fileName("conversations.json")
                .conversationCount(10).build();
        em.persist(upload);
        return upload;
    }

    private void saveConversation(Upload upload, User user, TeamTaskType taskType, String model, LocalDate date,
            String cost, int reviewSec, int reworkSec) {
        em.persist(Conversation.builder()
                .team(team).upload(upload).user(user).taskType(taskType)
                .externalId("cost-ext-" + (++externalIdSeq)).title("대화").model(model)
                .startedAt(date.atTime(10, 0)).conversationDate(date)
                .turnCount(4).estimatedTokens(1000).estimatedCost(cost != null ? new BigDecimal(cost) : null)
                .isSuccess(true).reviewTimeSec(reviewSec).reworkTimeSec(reworkSec)
                .build());
    }
}
