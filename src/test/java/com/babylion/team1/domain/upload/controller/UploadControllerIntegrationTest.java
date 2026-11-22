package com.babylion.team1.domain.upload.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UploadControllerIntegrationTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 11, 10);

    @TempDir
    static Path uploadDirectory;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtProvider jwtProvider;

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry properties) {
        properties.add("app.upload.directory", () -> uploadDirectory.toString());
    }

    @Test
    void storesUploadAndMarksOnlySameTeamConversationsAsDuplicates() throws Exception {
        User uploader = User.builder()
                .name("업로드 테스트")
                .email("upload-it-" + UUID.randomUUID() + "@test.com")
                .passwordHash("test-password-hash")
                .build();
        entityManager.persist(uploader);
        Team team = saveTeam(uploader, "업로드 테스트 팀");
        Team otherTeam = saveTeam(uploader, "다른 팀");

        TeamSettings settings = TeamSettings.createDefault(team);
        settings.updateTarget(TARGET_DATE, TARGET_DATE, null, null);
        entityManager.persist(settings);

        String sameTeamId = UUID.randomUUID().toString();
        String otherTeamId = UUID.randomUUID().toString();
        saveExistingConversation(team, uploader, sameTeamId);
        saveExistingConversation(otherTeam, uploader, otherTeamId);
        entityManager.flush();
        entityManager.clear();

        long epochSeconds = TARGET_DATE.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul")).toEpochSecond();
        String original = """
                [
                  {"id":"%s","title":"같은 팀 대화","create_time":%d,"mapping":{}},
                  {"id":"%s","title":"다른 팀 대화","create_time":%d,"mapping":{}}
                ]
                """.formatted(sameTeamId, epochSeconds, otherTeamId, epochSeconds);
        MockMultipartFile file = new MockMultipartFile(
                "file", "conversations.json", "application/json", original.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/teams/{teamId}/uploads", team.getId())
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(uploader.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.uploadId").isNumber())
                .andExpect(jsonPath("$.data.conversations[0].externalId").value(sameTeamId))
                .andExpect(jsonPath("$.data.conversations[0].isDuplicate").value(true))
                .andExpect(jsonPath("$.data.conversations[1].externalId").value(otherTeamId))
                .andExpect(jsonPath("$.data.conversations[1].isDuplicate").value(false));

        var row = jdbc.queryForMap("""
                select id, conversation_count, duplicate_count, status
                from uploads where team_id = ? and uploader_id = ? and file_name = 'conversations.json'
                """, team.getId(), uploader.getId());
        assertThat(row)
                .containsEntry("conversation_count", 2)
                .containsEntry("duplicate_count", 1)
                .containsEntry("status", "UPLOADED");
        long uploadId = ((Number) row.get("id")).longValue();
        assertThat(jdbc.queryForObject(
                "select count(*) from conversations where upload_id = ?", Long.class, uploadId)).isZero();
        assertThat(Files.readString(uploadDirectory.resolve(uploadId + ".export"))).isEqualTo(original);
    }

    @Test
    void analyzesUploadedConversationAndCommitsDone() throws Exception {
        User uploader = User.builder().name("분석 테스트").email("analysis-it-" + UUID.randomUUID() + "@test.com")
                .passwordHash("test-password-hash").build();
        entityManager.persist(uploader);
        Team team = saveTeam(uploader, "분석 테스트 팀");
        var settings = TeamSettings.createDefault(team);
        settings.updateTarget(TARGET_DATE, TARGET_DATE, null, null);
        entityManager.persist(settings);
        var type = TeamTaskType.builder().team(team).name("보고서").successDefinition("제출").build();
        entityManager.persist(type);
        entityManager.flush();

        long userId = uploader.getId();
        long teamId = team.getId();
        long typeId = type.getId();
        long epoch = TARGET_DATE.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul")).toEpochSecond();
        String externalId = UUID.randomUUID().toString();
        String export = """
                [{"id":"%s","title":"테스트","create_time":%d,"current_node":"answer","mapping":{
                  "question":{"parent":null,"children":["answer"],"message":{
                    "author":{"role":"user"},"content":{"parts":["hello"]},"create_time":%d}},
                  "answer":{"parent":"question","children":[],"message":{
                    "author":{"role":"assistant"},"content":{"parts":["world"]},"create_time":%d}}
                }}]
                """.formatted(externalId, epoch, epoch, epoch + 1);
        String bearer = "Bearer " + jwtProvider.createAccessToken(userId);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        try {
            mockMvc.perform(multipart("/api/v1/teams/{teamId}/uploads", teamId)
                            .file(new MockMultipartFile("file", "conversations.json", "application/json",
                                    export.getBytes(StandardCharsets.UTF_8)))
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isCreated());
            Long uploadId = jdbc.queryForObject("select id from uploads where team_id = ?", Long.class, teamId);

            mockMvc.perform(post("/api/v1/uploads/{uploadId}/analyses", uploadId)
                            .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer)
                            .content("""
                                    {"selections":[{"externalId":"%s","taskTypeId":%d}]}
                                    """.formatted(externalId, typeId)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.status").value("ANALYZING"));

            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
            Path original = uploadDirectory.resolve(uploadId + ".export");
            String state;
            do {
                state = jdbc.queryForObject("select status from uploads where id = ?", String.class, uploadId);
                if ("FAILED".equals(state) || ("DONE".equals(state) && Files.notExists(original))) {
                    break;
                }
                Thread.sleep(50);
            } while (System.nanoTime() < deadline);

            assertThat(state).as("fail_reason=%s", jdbc.queryForObject(
                    "select fail_reason from uploads where id = ?", String.class, uploadId)).isEqualTo("DONE");
            assertThat(original).doesNotExist();
            assertThat(jdbc.queryForObject("select progress from uploads where id = ?", Integer.class, uploadId))
                    .isEqualTo(100);
            assertThat(jdbc.queryForObject("select count(*) from uploads where id = ? and analyzed_at is not null",
                    Long.class, uploadId)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select estimated_tokens from conversations where upload_id = ?",
                    Integer.class, uploadId)).isEqualTo(2);
            assertThat(jdbc.queryForList("""
                    select m.content from messages m join conversations c on c.id = m.conversation_id
                    where c.upload_id = ? order by m.idx
                    """, String.class, uploadId)).containsExactly("hello", "world");
            mockMvc.perform(post("/api/v1/uploads/{uploadId}/analyses", uploadId)
                            .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer)
                            .content("""
                                    {"selections":[{"externalId":"%s","taskTypeId":%d}]}
                                    """.formatted(externalId, typeId)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ANALYSIS_ALREADY_COMPLETED"));
        } finally {
            jdbc.update("delete from messages where conversation_id in (select id from conversations where team_id = ?)",
                    teamId);
            jdbc.update("delete from conversations where team_id = ?", teamId);
            jdbc.update("delete from uploads where team_id = ?", teamId);
            jdbc.update("delete from team_task_types where team_id = ?", teamId);
            jdbc.update("delete from team_settings where team_id = ?", teamId);
            jdbc.update("delete from team_members where team_id = ?", teamId);
            jdbc.update("delete from teams where id = ?", teamId);
            jdbc.update("delete from users where id = ?", userId);
        }
    }

    private Team saveTeam(User uploader, String name) {
        Team team = Team.builder().name(name).createdBy(uploader).build();
        entityManager.persist(team);
        entityManager.persist(TeamMember.leader(team, uploader));
        return team;
    }

    private void saveExistingConversation(Team team, User uploader, String externalId) {
        TeamTaskType type = TeamTaskType.builder().team(team).name("보고서").successDefinition("제출").build();
        entityManager.persist(type);
        Upload previous = Upload.builder().team(team).uploader(uploader).fileName("previous.json")
                .conversationCount(1).build();
        entityManager.persist(previous);
        entityManager.persist(Conversation.builder()
                .team(team).upload(previous).user(uploader).taskType(type).externalId(externalId)
                .title("기존 대화").startedAt(TARGET_DATE.atTime(10, 0)).conversationDate(TARGET_DATE)
                .turnCount(1).estimatedTokens(1).isSuccess(true).build());
    }
}
