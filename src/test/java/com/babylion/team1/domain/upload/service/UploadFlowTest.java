package com.babylion.team1.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.upload.controller.UploadController;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.exception.GlobalExceptionHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class UploadFlowTest {

    private static final String URL = "/api/v1/teams/{teamId}/uploads";
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    private static final String IN_PERIOD = "2026-11-09T15:00:00Z";
    private static final long TEAM_ID = 1L;
    private static final long USER_ID = 2L;
    private static final long UPLOAD_ID = 10L;

    @TempDir
    Path directory;

    private final TeamMemberRepository members = mock(TeamMemberRepository.class);
    private final TeamSettingsRepository settingsRepository = mock(TeamSettingsRepository.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final UploadRepository uploads = mock(UploadRepository.class);
    private final Team team = mock(Team.class);
    private final User user = mock(User.class);
    private TeamSettings settings;
    private UploadFileStorage storage;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        settings = TeamSettings.createDefault(team);
        settings.updateTarget(DAY, DAY, null, null);
        when(members.findByTeamIdAndUserId(TEAM_ID, USER_ID))
                .thenReturn(Optional.of(TeamMember.member(team, user)));
        when(settingsRepository.findByTeamId(TEAM_ID)).thenReturn(Optional.of(settings));
        when(uploads.saveAndFlush(any(Upload.class))).thenAnswer(invocation -> {
            Upload upload = invocation.getArgument(0);
            ReflectionTestUtils.setField(upload, "id", UPLOAD_ID);
            return upload;
        });

        storage = new UploadFileStorage(directory.toString());
        UploadService service = new UploadService(members, settingsRepository, conversations, uploads,
                new ChatGptExportReader(DataSize.ofBytes(512)), storage);
        mvc = MockMvcBuilders.standaloneSetup(new UploadController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new TestPrincipalResolver())
                .build();
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void jsonReturnsKstCandidatesAndTeamDuplicatesAndStoresTheOriginal() throws Exception {
        String json = "[" + conversation("duplicate", IN_PERIOD) + ","
                + conversation("new", "2026-11-10T14:59:59Z") + ","
                + conversation("too-early", "2026-11-09T14:59:59.9999999Z") + ","
                + conversation("too-late", "2026-11-10T15:00:00Z") + "]";
        when(conversations.findExistingExternalIds(TEAM_ID, List.of("duplicate", "new")))
                .thenReturn(List.of("duplicate"));

        upload("conversations.json", json.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S201"))
                .andExpect(jsonPath("$.message").value("업로드 성공"))
                .andExpect(jsonPath("$.data.uploadId").value(UPLOAD_ID))
                .andExpect(jsonPath("$.data.conversations.length()").value(2))
                .andExpect(jsonPath("$.data.conversations[0].externalId").value("duplicate"))
                .andExpect(jsonPath("$.data.conversations[0].title").value("테스트"))
                .andExpect(jsonPath("$.data.conversations[0].startedAt").value("2026-11-10T00:00:00+09:00"))
                .andExpect(jsonPath("$.data.conversations[0].isDuplicate").value(true))
                .andExpect(jsonPath("$.data.conversations[1].isDuplicate").value(false));

        ArgumentCaptor<Upload> captured = ArgumentCaptor.forClass(Upload.class);
        verify(uploads).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getTeam()).isSameAs(team);
        assertThat(captured.getValue().getUploader()).isSameAs(user);
        assertThat(captured.getValue().getStatus()).isEqualTo(UploadStatus.UPLOADED);
        assertThat(captured.getValue().getConversationCount()).isEqualTo(2);
        assertThat(captured.getValue().getDuplicateCount()).isEqualTo(1);
        assertThat(Files.readString(storage.pathFor(UPLOAD_ID))).isEqualTo(json);
    }

    @Test
    void zipReturnsCandidatesAndKeepsTheArchiveBytes() throws Exception {
        byte[] archive = archive("export/conversations.json", "[" + conversation("zip-id", IN_PERIOD) + "]");

        upload("export.zip", archive)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversations[0].externalId").value("zip-id"));
        assertThat(Files.readAllBytes(storage.pathFor(UPLOAD_ID))).isEqualTo(archive);
    }

    @Test
    void validExportWithNoConversationInTheTargetPeriodReturnsAnEmptyList() throws Exception {
        upload("conversations.json", bytes("[" + conversation("old", "2026-11-09T00:00:00Z") + "]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversations").isEmpty());
        verifyNoInteractions(conversations);
    }

    @Test
    void rejectsMalformedJsonAndMissingRequiredFieldsAndRemovesStagingFile() throws Exception {
        upload("conversations.json", bytes("[{\"id\":\"x\",\"mapping\":{}}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_FORMAT"));
        upload("conversations.json", bytes("[]{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_FORMAT"));
        assertThat(directory).isEmptyDirectory();
        verifyNoInteractions(uploads);
    }

    @Test
    void rejectsMissingMultipartFile() throws Exception {
        mvc.perform(multipart(URL, TEAM_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_FORMAT"));
    }

    @Test
    void rejectsZipWithoutExportAndRejectsAnOversizedJsonEntry() throws Exception {
        upload("missing.zip", archive("user.json", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_FORMAT"));
        upload("oversized.zip", archive("conversations.json", "[]" + " ".repeat(512)))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void servletSizeLimitUsesTheSameErrorCode() {
        var response = new GlobalExceptionHandler().handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(512));
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody().getCode()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void requiresTeamMembershipAndTargetPeriod() throws Exception {
        mvc.perform(multipart(URL, 99L).file(multipartFile("conversations.json", bytes("[]"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));

        settings.updateTarget(DAY, null, null, null);
        upload("conversations.json", bytes("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_SET"));

        verifyNoInteractions(uploads);
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void transactionRollbackRemovesStoredFile() throws Exception {
        upload("conversations.json", bytes("[]")).andExpect(status().isCreated());
        assertThat(storage.pathFor(UPLOAD_ID)).exists();

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void expiresUnsubmittedOriginalButKeepsAnalyzingOriginal() throws Exception {
        Upload unsubmitted = mock(Upload.class);
        when(unsubmitted.getStatus()).thenReturn(UploadStatus.UPLOADED);
        when(unsubmitted.getUploadedAt()).thenReturn(LocalDateTime.now().minusHours(25));
        when(uploads.findByIdForUpdate(10L)).thenReturn(Optional.of(unsubmitted));
        Upload analyzing = mock(Upload.class);
        when(analyzing.getStatus()).thenReturn(UploadStatus.ANALYZING);
        when(uploads.findByIdForUpdate(11L)).thenReturn(Optional.of(analyzing));
        for (long id : List.of(10L, 11L)) {
            Path file = Files.writeString(storage.pathFor(id), "[]");
            Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(25 * 3600)));
        }
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());

        new UploadFileExpiry(storage, uploads, manager).expireUnsubmittedFiles();

        assertThat(storage.pathFor(10L)).doesNotExist();
        assertThat(storage.pathFor(11L)).exists();
    }

    private ResultActions upload(String name, byte[] body) throws Exception {
        return mvc.perform(multipart(URL, TEAM_ID).file(multipartFile(name, body)));
    }

    private static MockMultipartFile multipartFile(String name, byte[] body) {
        return new MockMultipartFile("file", name, "application/octet-stream", body);
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static String conversation(String id, String instant) {
        return """
                {"id":"%s","title":"테스트","create_time":%s,"mapping":{}}
                """.formatted(id, epochSeconds(Instant.parse(instant)));
    }

    private static String epochSeconds(Instant instant) {
        return BigDecimal.valueOf(instant.getEpochSecond())
                .add(BigDecimal.valueOf(instant.getNano(), 9))
                .toPlainString();
    }

    private static byte[] archive(String entryName, String json) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(bytes(json));
            output.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static class TestPrincipalResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                      NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return USER_ID;
        }
    }
}
