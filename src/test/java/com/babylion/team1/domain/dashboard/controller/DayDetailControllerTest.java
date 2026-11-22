package com.babylion.team1.domain.dashboard.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.jwt.JwtProvider;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 2026-11-05 대화
 *  - member1 / 주간 보고서 작성 / 10:00 "보고서 초안" gpt-4o 4턴 1000토큰 성공
 *  - member1 / 회의록 정리      / 09:00 "회의록 요약" o3     6턴 1500토큰 실패 has_rework
 *  - member2 / 주간 보고서 작성 / 11:00 "팀원2 비밀 제목" 2000토큰 성공 has_rework
 *  - 다른 팀 대화 (같은 날, "다른 팀 대화")
 * 2026-11-06 대화
 *  - member1 / 주간 보고서 작성 / "다음 날 대화"
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DayDetailControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 5);

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
        leader = saveUser("팀장", "day-it-leader-" + suffix + "@test.com");
        member1 = saveUser("김팀원", "day-it-member1-" + suffix + "@test.com");
        User member2 = saveUser("이팀원", "day-it-member2-" + suffix + "@test.com");
        outsider = saveUser("외부인", "day-it-outsider-" + suffix + "@test.com");

        team = saveTeam("기획팀", leader);
        em.persist(TeamMember.leader(team, leader));
        em.persist(TeamMember.member(team, member1));
        em.persist(TeamMember.member(team, member2));

        TeamTaskType report = saveTaskType(team, "주간 보고서 작성");
        TeamTaskType minutes = saveTaskType(team, "회의록 정리");

        Upload upload1 = saveUpload(team, member1);
        Upload upload2 = saveUpload(team, member2);

        saveConversation(team, upload1, member1, report, "보고서 초안", "gpt-4o", DAY.atTime(10, 0), 4, 1000, true, false);
        saveConversation(team, upload1, member1, minutes, "회의록 요약", "o3", DAY.atTime(9, 0), 6, 1500, false, true);
        saveConversation(team, upload2, member2, report, "팀원2 비밀 제목", "gpt-4o", DAY.atTime(11, 0), 4, 2000, true, true);
        saveConversation(team, upload1, member1, report, "다음 날 대화", "gpt-4o", DAY.plusDays(1).atTime(10, 0), 4, 500,
                true, false);

        // 같은 날 다른 팀 대화: outsider가 팀장
        Team otherTeam = saveTeam("다른 팀", outsider);
        em.persist(TeamMember.leader(otherTeam, outsider));
        TeamTaskType otherTaskType = saveTaskType(otherTeam, "다른 팀 업무");
        saveConversation(otherTeam, saveUpload(otherTeam, outsider), outsider, otherTaskType, "다른 팀 대화", "gpt-4o",
                DAY.atTime(12, 0), 4, 700, true, false);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("팀원이 조회하면 그날 본인 대화만 startedAt 오름차순으로 응답하고 taskTypes는 null이다")
    void memberGetsOwnConversations() throws Exception {
        perform(member1, url(team.getId(), "2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.date").value("2026-11-05"))
                .andExpect(jsonPath("$.data.taskTypes").isEmpty())
                // 다른 팀원 대화, 다른 날 대화는 빠진다
                .andExpect(jsonPath("$.data.conversations", hasSize(2)))
                .andExpect(jsonPath("$.data.conversations[0].conversationId").isNumber())
                .andExpect(jsonPath("$.data.conversations[0].title").value("회의록 요약"))
                .andExpect(jsonPath("$.data.conversations[0].startedAt", startsWith("2026-11-05T09:00")))
                .andExpect(jsonPath("$.data.conversations[0].model").value("o3"))
                .andExpect(jsonPath("$.data.conversations[0].taskType").value("회의록 정리"))
                .andExpect(jsonPath("$.data.conversations[0].turnCount").value(6))
                .andExpect(jsonPath("$.data.conversations[0].estimatedTokens").value(1500))
                .andExpect(jsonPath("$.data.conversations[0].isSuccess").value(false))
                .andExpect(jsonPath("$.data.conversations[0].hasRework").value(true))
                .andExpect(jsonPath("$.data.conversations[1].title").value("보고서 초안"))
                .andExpect(jsonPath("$.data.conversations[1].taskType").value("주간 보고서 작성"))
                .andExpect(content().string(not(containsString("팀원2 비밀 제목"))));
    }

    @Test
    @DisplayName("팀장이 조회하면 그날 팀 전체 대화를 업무 유형별로 집계해 응답하고 conversations는 null이다")
    void leaderGetsTaskTypeAggregate() throws Exception {
        perform(leader, url(team.getId(), "2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-05"))
                .andExpect(jsonPath("$.data.conversations").isEmpty())
                .andExpect(jsonPath("$.data.taskTypes", hasSize(2)))
                // 주간 보고서 작성: 2건(member1, member2), 재작업 1, 토큰 3000 / 성공 2
                .andExpect(jsonPath("$.data.taskTypes[0].name").value("주간 보고서 작성"))
                .andExpect(jsonPath("$.data.taskTypes[0].count").value(2))
                .andExpect(jsonPath("$.data.taskTypes[0].reworkRate").value(0.5))
                .andExpect(jsonPath("$.data.taskTypes[0].tokensPerSuccess").value(1500.0))
                // 회의록 정리: 1건, 성공 0 -> 성공 업무당 토큰 null
                .andExpect(jsonPath("$.data.taskTypes[1].name").value("회의록 정리"))
                .andExpect(jsonPath("$.data.taskTypes[1].count").value(1))
                .andExpect(jsonPath("$.data.taskTypes[1].reworkRate").value(1.0))
                .andExpect(jsonPath("$.data.taskTypes[1].tokensPerSuccess").isEmpty());
    }

    @Test
    @DisplayName("팀장 응답에는 대화 제목·사용자 등 개인 식별 정보가 들어가지 않는다")
    void leaderResponseHasNoPersonalInformation() throws Exception {
        perform(leader, url(team.getId(), "2026-11-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskTypes[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.taskTypes[0].title").doesNotExist())
                .andExpect(content().string(not(containsString("보고서 초안"))))
                .andExpect(content().string(not(containsString("팀원2 비밀 제목"))))
                .andExpect(content().string(not(containsString("김팀원"))))
                .andExpect(content().string(not(containsString("다른 팀"))));
    }

    @Test
    @DisplayName("대화가 없는 날은 팀원·팀장 모두 빈 목록을 응답한다")
    void noConversations() throws Exception {
        perform(member1, url(team.getId(), "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations", hasSize(0)))
                .andExpect(jsonPath("$.data.taskTypes").isEmpty());

        perform(leader, url(team.getId(), "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations").isEmpty())
                .andExpect(jsonPath("$.data.taskTypes", hasSize(0)));
    }

    @Test
    @DisplayName("날짜 형식이 잘못되면 400 INVALID_REQUEST로 응답한다")
    void invalidDate() throws Exception {
        for (String date : new String[] {"20261105", "2026-13-01", "2026-02-30", "abc"}) {
            perform(member1, url(team.getId(), date))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    @DisplayName("팀 멤버가 아니면 403 NOT_A_MEMBER, 없는 팀이면 404 TEAM_NOT_FOUND로 응답한다")
    void accessDenied() throws Exception {
        perform(outsider, url(team.getId(), "2026-11-05"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));

        perform(leader, url(team.getId() + 999_999, "2026-11-05"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("API 문서에 date가 날짜 형식의 path 파라미터로 노출된다")
    void apiDocsExposeDate() throws Exception {
        String date = "$.paths['/api/v1/teams/{teamId}/days/{date}'].get.parameters[?(@.name == 'date')]";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(date + ".in").value("path"))
                .andExpect(jsonPath(date + ".schema.format").value("date"));
    }

    private ResultActions perform(User user, String url) throws Exception {
        return mockMvc.perform(get(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId())));
    }

    private String url(Long teamId, String date) {
        return "/api/v1/teams/" + teamId + "/days/" + date;
    }

    private User saveUser(String name, String email) {
        User user = User.builder().name(name).email(email).passwordHash("hashed-password").build();
        em.persist(user);
        return user;
    }

    private Team saveTeam(String name, User createdBy) {
        Team saved = Team.builder().name(name).description("설명").createdBy(createdBy).build();
        em.persist(saved);
        return saved;
    }

    private TeamTaskType saveTaskType(Team owner, String name) {
        TeamTaskType taskType = TeamTaskType.builder().team(owner).name(name).successDefinition("성공 정의").build();
        em.persist(taskType);
        return taskType;
    }

    private Upload saveUpload(Team owner, User uploader) {
        Upload upload = Upload.builder().team(owner).uploader(uploader).fileName("conversations.json")
                .conversationCount(10).build();
        em.persist(upload);
        return upload;
    }

    private void saveConversation(Team owner, Upload upload, User user, TeamTaskType taskType, String title,
            String model, LocalDateTime startedAt, int turns, int tokens, boolean success, boolean rework) {
        em.persist(Conversation.builder()
                .team(owner).upload(upload).user(user).taskType(taskType)
                .externalId("day-ext-" + (++externalIdSeq)).title(title).model(model)
                .startedAt(startedAt).conversationDate(startedAt.toLocalDate())
                .turnCount(turns).estimatedTokens(tokens)
                .isSuccess(success).hasRework(rework)
                .build());
    }
}
