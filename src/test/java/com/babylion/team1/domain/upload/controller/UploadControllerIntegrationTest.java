package com.babylion.team1.domain.upload.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
