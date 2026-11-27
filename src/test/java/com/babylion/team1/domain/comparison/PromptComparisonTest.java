package com.babylion.team1.domain.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.babylion.team1.domain.comparison.PromptComparisonResult.Status;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PromptComparisonTest {

    private static final PromptComparisonRequest REQUEST = new PromptComparisonRequest("공통 업무", "기존", "개선");
    private final ObjectMapper mapper = new ObjectMapper();
    private final Queue<JsonNode> requests = new ConcurrentLinkedQueue<>();
    private final Queue<String> authorizations = new ConcurrentLinkedQueue<>();
    private final TeamMemberRepository members = mock(TeamMemberRepository.class);
    private HttpServer provider;
    private URI endpoint;

    @BeforeEach
    void startProvider() throws IOException {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v1/responses", exchange -> {
            try (exchange) {
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                requests.add(request);
                authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
                int status = 200;
                String body = switch (request.path("instructions").asString()) {
                    case "기존" -> response("completed", 100, 50, 30);
                    case "개선" -> response("completed", 80, 20, 0);
                    case "잘림" -> response("incomplete", 80, 1024, 0);
                    default -> null;
                };
                if (body == null) {
                    status = 429;
                    body = "{\"error\":{\"message\":\"internal provider text\"}}";
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("x-request-id", "req_fixture");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        provider.start();
        endpoint = URI.create("http://127.0.0.1:" + provider.getAddress().getPort() + "/v1/responses");
        when(members.findByTeamIdAndUserId(5L, 1L)).thenReturn(Optional.of(mock(TeamMember.class)));
    }

    @AfterEach
    void stopProvider() {
        provider.stop(0);
    }

    @Test
    void recordsReportedUsageForTwoIndependentRequestsWithIdenticalTaskAndLimits() {
        var result = service("fixture-key").compare(5L, 1L, REQUEST);

        assertThat(result.baseline().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.baseline().usage()).isEqualTo(new PromptComparisonResult.Usage(100, 50, 150, 30));
        assertThat(result.improved().usage()).isEqualTo(new PromptComparisonResult.Usage(80, 20, 100, 0));
        assertThat(result.improved().output()).isEqualTo("응답 본문");
        assertThat(result.improved().providerIds()).isEqualTo(
                new PromptComparisonResult.ProviderIds("req_fixture", "resp_fixture"));
        assertThat(authorizations).containsExactly("Bearer fixture-key", "Bearer fixture-key");
        assertThat(requests).extracting(request -> request.path("instructions").asString())
                .containsExactlyInAnyOrder("기존", "개선");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.path("input").asString()).isEqualTo(REQUEST.task());
            assertThat(request.path("model").asString()).isEqualTo("fixture-model");
            assertThat(request.path("max_output_tokens").asInt()).isEqualTo(1024);
            assertThat(request.path("store").asBoolean()).isFalse();
            assertThat(request.has("previous_response_id")).isFalse();
        });
    }

    @Test
    void retainsReportedUsageForAnIncompleteResponseAndDoesNotRetryTheFailedRequest() {
        var result = service("fixture-key").compare(5L, 1L, new PromptComparisonRequest("공통 업무", "잘림", "요청 제한"));

        assertThat(result.baseline().status()).isEqualTo(Status.INCOMPLETE);
        assertThat(result.baseline().usage().totalTokens()).isEqualTo(1104);
        assertThat(result.improved().status()).isEqualTo(Status.FAILED);
        assertThat(result.improved().usage()).isNull();
        assertThat(result.improved().message()).contains("사용량 한도").doesNotContain("internal provider text");
        assertThat(requests).hasSize(2);
    }

    @Test
    void rejectsNonMembersAndMissingConfigurationBeforeCallingProvider() {
        var configured = service("fixture-key");
        assertThatThrownBy(() -> configured.compare(5L, 2L, REQUEST))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_A_MEMBER));
        assertThatThrownBy(() -> service("").compare(5L, 1L, REQUEST))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PROMPT_COMPARISON_UNAVAILABLE));
        assertThat(requests).isEmpty();
    }

    private PromptComparisonService service(String key) {
        return new PromptComparisonService(members, new OpenAiComparisonClient(key, "fixture-model", endpoint));
    }

    private static String response(String status, int input, int output, int cached) {
        return """
                {"id":"resp_fixture","status":"%s","model":"fixture-model-2026-01-01",
                 "output":[{"type":"reasoning"},{"type":"message","content":[{"type":"output_text","text":"응답 본문"}]}],
                 "usage":{"input_tokens":%d,"output_tokens":%d,"total_tokens":%d,"input_tokens_details":{"cached_tokens":%d}},
                 "incomplete_details":{"reason":"max_output_tokens"}}
                """.formatted(status, input, output, input + output, cached);
    }
}
