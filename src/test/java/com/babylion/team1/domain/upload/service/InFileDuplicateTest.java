package com.babylion.team1.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class InFileDuplicateTest {
    @TempDir Path directory;
    private final ChatGptExportReader reader = new ChatGptExportReader(DataSize.ofMegabytes(10));
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private String conversation(String text, long time) {
        return """
                {"id":"same-id","title":"demo","create_time":%d,"current_node":"u","mapping":{
                  "u":{"parent":null,"children":[],"message":{"author":{"role":"user"},
                  "create_time":%d,"content":{"parts":["%s"]},"metadata":{}}}}}
                """.formatted(time, time, text);
    }

    @Test void identicalIdsAreOneCandidateAndOneSelectedSource() throws Exception {
        String row = conversation("[DEMO] hello", 1795132800);
        Path file = Files.writeString(directory.resolve("same.json"), "[" + row + "," + row + "]");
        var result = reader.readCandidates(file, false, DAY.minusDays(5), DAY.plusDays(5));
        assertThat(result.conversationCount()).isEqualTo(2);
        assertThat(result.candidates()).hasSize(1);
        assertThat(reader.readSelected(file, false, Set.of("same-id"))).hasSize(1);
    }

    @Test void conflictingContentIsRejectedForJsonAndSelectedSource() throws Exception {
        Path file = Files.writeString(directory.resolve("conflict.json"), "["
                + conversation("[DEMO] first", 1795132800) + ","
                + conversation("[DEMO] other", 1795132800) + "]");
        assertConflict(() -> reader.readCandidates(file, false, DAY.minusDays(5), DAY.plusDays(5)));
        assertConflict(() -> reader.readSelected(file, false, Set.of("same-id")));
    }

    @Test void dateFilteringCannotMakeMetadataAndSelectedSourceDisagree() throws Exception {
        Path file = Files.writeString(directory.resolve("date.json"), "["
                + conversation("[DEMO] first", 1700000000) + ","
                + conversation("[DEMO] other", 1795132800) + "]");
        assertConflict(() -> reader.readCandidates(file, false, DAY.minusDays(5), DAY.plusDays(5)));
    }

    @Test void zipUsesTheSameConflictPolicy() throws Exception {
        Path file = directory.resolve("conflict.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("conversations.json"));
            zip.write(("[" + conversation("[DEMO] first", 1795132800) + ","
                    + conversation("[DEMO] other", 1795132800) + "]").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertConflict(() -> reader.readCandidates(file, true, DAY.minusDays(5), DAY.plusDays(5)));
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(CustomException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICTING_CONVERSATION_ID));
    }
}
