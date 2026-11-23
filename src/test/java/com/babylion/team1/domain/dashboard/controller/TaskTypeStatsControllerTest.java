package com.babylion.team1.domain.dashboard.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
 * 대상 기간 2026-11-01 ~ 2026-11-20. 업무 유형 A, B, C, D(D는 대화 없음)
 *
 * 대화
 *  - A: member1 11-02 성공 1000토큰 / member2 11-03 성공 2000토큰 has_rework
 *       -> 2건, 재작업률 0.5, 성공 업무당 토큰 1500
 *  - B: member1 11-05 실패 500토큰 has_rework / member2 11-06 성공 1500토큰 has_rework / member1 11-07 성공 1000토큰
 *       -> 3건, 재작업률 0.6667, 성공 업무당 토큰 1500
 *  - C: member2 11-08 성공 800토큰 -> 1건, 재작업률 0, 성공 업무당 토큰 800
 *  - 대상 기간 밖: A member1 10-31 has_rework (집계 제외)
 * 피드백(기간 안)
 *  - A: VAGUE_REQUEST 3건, MISSING_FORMAT 2건, REPEAT_FIX 2건, TOO_LONG 1건
 *  - B: REPEAT_FIX 1건
 *  - 대상 기간 밖: A TOO_LONG 5건 (10-31, 집계 제외)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(DashboardFixture.class)
class TaskTypeStatsControllerTest {

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
    private TeamTaskType taskTypeA;
    private TeamTaskType taskTypeB;
    private TeamTaskType taskTypeC;

    @BeforeEach
    void setUp() {
        // 테스트도 로컬 DB를 쓰므로, 직접 가입해 둔 계정과 겹치지 않게 실행마다 고유한 이메일을 쓴다
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        leader = fixture.user("팀장", "task-stats-it-leader-" + suffix + "@test.com");
        member1 = fixture.user("김팀원", "task-stats-it-member1-" + suffix + "@test.com");
        User member2 = fixture.user("이팀원", "task-stats-it-member2-" + suffix + "@test.com");
        outsider = fixture.user("외부인", "task-stats-it-outsider-" + suffix + "@test.com");

        team = fixture.team("기획팀", leader);
        em.persist(TeamMember.leader(team, leader));
        em.persist(TeamMember.member(team, member1));
        em.persist(TeamMember.member(team, member2));

        TeamSettings settings = TeamSettings.createDefault(team);
        settings.updateTarget(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20), null, null);
        em.persist(settings);

        taskTypeA = fixture.taskType(team, "주간 보고서 작성");
        taskTypeB = fixture.taskType(team, "회의록 정리");
        taskTypeC = fixture.taskType(team, "고객 응대");
        fixture.taskType(team, "대화 없는 업무");

        Upload upload1 = fixture.upload(team, member1);
        Upload upload2 = fixture.upload(team, member2);

        saveConversation(upload1, member1, taskTypeA, LocalDate.of(2026, 11, 2), true, false, 1000);
        saveConversation(upload2, member2, taskTypeA, LocalDate.of(2026, 11, 3), true, true, 2000);
        saveConversation(upload1, member1, taskTypeB, LocalDate.of(2026, 11, 5), false, true, 500);
        saveConversation(upload2, member2, taskTypeB, LocalDate.of(2026, 11, 6), true, true, 1500);
        saveConversation(upload1, member1, taskTypeB, LocalDate.of(2026, 11, 7), true, false, 1000);
        saveConversation(upload2, member2, taskTypeC, LocalDate.of(2026, 11, 8), true, false, 800);
        saveConversation(upload1, member1, taskTypeA, LocalDate.of(2026, 10, 31), false, true, 9999);

        LocalDate nov5 = LocalDate.of(2026, 11, 5);
        saveFeedbacks(upload1, member1, taskTypeA, nov5, "VAGUE_REQUEST", 3);
        saveFeedbacks(upload2, member2, taskTypeA, nov5, "MISSING_FORMAT", 2);
        saveFeedbacks(upload1, member1, taskTypeA, nov5, "REPEAT_FIX", 2);
        saveFeedbacks(upload1, member1, taskTypeA, nov5, "TOO_LONG", 1);
        saveFeedbacks(upload2, member2, taskTypeB, nov5, "REPEAT_FIX", 1);
        saveFeedbacks(upload1, member1, taskTypeA, LocalDate.of(2026, 10, 31), "TOO_LONG", 5);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("팀장이 조회하면 대상 기간의 업무 유형별 집계를 재작업률 높은 순으로 응답한다")
    void leaderGetsStatsSortedByReworkRate() throws Exception {
        perform(leader, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                // 대화 없는 업무 유형(D)은 빠진다
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].taskType.id").value(taskTypeB.getId()))
                .andExpect(jsonPath("$.data.items[0].taskType.name").value("회의록 정리"))
                .andExpect(jsonPath("$.data.items[0].reworkRate").value(0.6667))
                .andExpect(jsonPath("$.data.items[0].tokensPerSuccess").value(1500.0))
                .andExpect(jsonPath("$.data.items[0].conversationCount").value(3))
                // 대상 기간 밖 대화(10-31)는 집계에서 빠진다
                .andExpect(jsonPath("$.data.items[1].taskType.id").value(taskTypeA.getId()))
                .andExpect(jsonPath("$.data.items[1].reworkRate").value(0.5))
                .andExpect(jsonPath("$.data.items[1].tokensPerSuccess").value(1500.0))
                .andExpect(jsonPath("$.data.items[1].conversationCount").value(2))
                .andExpect(jsonPath("$.data.items[2].taskType.id").value(taskTypeC.getId()))
                .andExpect(jsonPath("$.data.items[2].reworkRate").value(0.0))
                .andExpect(jsonPath("$.data.items[2].tokensPerSuccess").value(800.0));
    }

    @Test
    @DisplayName("업무 유형별 상위 피드백 유형을 건수 많은 순으로 최대 3개 응답한다 (같으면 이름순, 없으면 빈 목록)")
    void topFeedbackTypes() throws Exception {
        perform(leader, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].topFeedbackTypes", contains("REPEAT_FIX")))
                // 기간 밖 TOO_LONG 5건은 제외, 동률(2건)은 이름순
                .andExpect(jsonPath("$.data.items[1].topFeedbackTypes",
                        contains("VAGUE_REQUEST", "MISSING_FORMAT", "REPEAT_FIX")))
                .andExpect(jsonPath("$.data.items[2].topFeedbackTypes", hasSize(0)));
    }

    @Test
    @DisplayName("응답에는 팀원 개인을 식별할 수 있는 정보가 들어가지 않는다")
    void noPersonalInformation() throws Exception {
        perform(leader, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].users").doesNotExist())
                .andExpect(content().string(not(containsString("김팀원"))))
                .andExpect(content().string(not(containsString("이팀원"))))
                .andExpect(content().string(not(containsString("task-stats-it-member"))));
    }

    @Test
    @DisplayName("from·to를 넣으면 그 기간으로 집계한다")
    void customPeriod() throws Exception {
        perform(leader, url(team.getId()) + "?from=2026-11-05&to=2026-11-08")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].taskType.id").value(taskTypeB.getId()))
                .andExpect(jsonPath("$.data.items[1].taskType.id").value(taskTypeC.getId()));
    }

    @Test
    @DisplayName("기간 안에 데이터가 없으면 빈 목록을 응답한다")
    void noData() throws Exception {
        perform(leader, url(team.getId()) + "?from=2026-09-01&to=2026-09-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("팀원이 호출하면 403 LEADER_ONLY로 응답한다")
    void memberIsForbidden() throws Exception {
        perform(member1, url(team.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_ONLY"));
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
    @DisplayName("from이 to보다 늦거나 날짜 형식이 잘못되면 400 INVALID_REQUEST로 응답한다")
    void invalidPeriod() throws Exception {
        perform(leader, url(team.getId()) + "?from=2026-11-20&to=2026-11-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        perform(leader, url(team.getId()) + "?from=2026/11/01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private ResultActions perform(User user, String url) throws Exception {
        return mockMvc.perform(fixture.get(user, url));
    }

    private String url(Long teamId) {
        return "/api/v1/teams/" + teamId + "/dashboard/task-types";
    }

    private void saveConversation(Upload upload, User user, TeamTaskType taskType, LocalDate date,
            boolean success, boolean rework, int tokens) {
        fixture.save(fixture.conversation(upload, user, taskType, date)
                .estimatedTokens(tokens).isSuccess(success).hasRework(rework));
    }

    private void saveFeedbacks(Upload upload, User user, TeamTaskType taskType, LocalDate date, String type,
            int count) {
        for (int i = 0; i < count; i++) {
            fixture.save(fixture.feedback(upload, user, taskType, date).type(type));
        }
    }
}
