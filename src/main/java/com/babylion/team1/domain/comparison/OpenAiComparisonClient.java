package com.babylion.team1.domain.comparison;

import com.babylion.team1.domain.comparison.PromptComparisonResult.ProviderIds;
import com.babylion.team1.domain.comparison.PromptComparisonResult.Run;
import com.babylion.team1.domain.comparison.PromptComparisonResult.Status;
import com.babylion.team1.domain.comparison.PromptComparisonResult.Usage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class OpenAiComparisonClient {

    private static final String UNEXPECTED_RESPONSE = "OpenAI 응답에서 결과를 확인할 수 없습니다.";
    private static final int MAX_OUTPUT_TOKENS = 1_024;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI endpoint;
    private final String apiKey;
    private final String model;

    @Autowired
    OpenAiComparisonClient(@Value("${app.prompt-comparison.api-key}") String apiKey,
                           @Value("${app.prompt-comparison.model}") String model) {
        this(apiKey, model, URI.create("https://api.openai.com/v1/responses"));
    }

    OpenAiComparisonClient(String apiKey, String model, URI endpoint) {
        this.apiKey = apiKey.strip();
        this.model = model.strip();
        this.endpoint = endpoint;
    }

    PromptComparisonResult.Configuration configuration() {
        return new PromptComparisonResult.Configuration(!apiKey.isBlank() && !model.isBlank(), model);
    }

    CompletableFuture<Run> run(String task, String prompt) {
        var payload = Map.of(
                "model", model,
                "input", task,
                "instructions", prompt,
                "max_output_tokens", MAX_OUTPUT_TOKENS,
                "store", false);
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(18))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> error == null
                        ? decode(response)
                        : Run.failed("OpenAI 응답을 제시간에 받지 못했습니다. 요청이 처리됐을 수 있습니다."));
    }

    private Run decode(HttpResponse<String> response) {
        String requestId = response.headers().firstValue("x-request-id").orElse(null);
        if (response.statusCode() != 200) {
            return Run.failed(switch (response.statusCode()) {
                case 401, 403, 404 -> "관리자가 OpenAI API 키와 모델 사용 권한을 확인해야 합니다.";
                case 429 -> "OpenAI 사용량 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.";
                default -> "OpenAI가 요청을 처리하지 못했습니다.";
            }, new ProviderIds(requestId, null));
        }
        try {
            JsonNode json = mapper.readTree(response.body());
            if (json == null || !json.isObject()) return Run.failed(UNEXPECTED_RESPONSE, new ProviderIds(requestId, null));
            var ids = new ProviderIds(requestId, json.path("id").asString(null));
            String resolvedModel = json.path("model").asString("");
            Usage usage = usage(json.path("usage"));
            var text = new StringBuilder();
            var refusal = new StringBuilder();
            for (JsonNode item : json.path("output")) {
                if (!"message".equals(item.path("type").asString(""))) continue;
                for (JsonNode content : item.path("content")) {
                    switch (content.path("type").asString("")) {
                        case "output_text" -> append(text, content.path("text").asString(""));
                        case "refusal" -> append(refusal, content.path("refusal").asString(""));
                        default -> { }
                    }
                }
            }
            String status = json.path("status").asString("");
            if (!refusal.isEmpty()) {
                return new Run(Status.REFUSED, resolvedModel, refusal.toString(), usage,
                        "OpenAI가 이 요청에 대한 답변을 거절했습니다.", ids);
            }
            if ("incomplete".equals(status)) {
                return new Run(Status.INCOMPLETE, resolvedModel, text.toString(), usage,
                        "max_output_tokens".equals(json.path("incomplete_details").path("reason").asString(""))
                                ? "출력 토큰 상한에 도달해 응답이 중단됐습니다."
                                : "OpenAI 응답이 완료되지 않았습니다.", ids);
            }
            if ("completed".equals(status) && !resolvedModel.isBlank() && !text.isEmpty() && usage != null) {
                return new Run(Status.COMPLETED, resolvedModel, text.toString(), usage, null, ids);
            }
            return new Run(Status.FAILED, resolvedModel, text.toString(), usage, UNEXPECTED_RESPONSE, ids);
        } catch (JacksonException exception) {
            return Run.failed(UNEXPECTED_RESPONSE, new ProviderIds(requestId, null));
        }
    }

    private static Usage usage(JsonNode json) {
        Integer input = tokenCount(json.path("input_tokens"));
        Integer output = tokenCount(json.path("output_tokens"));
        Integer total = tokenCount(json.path("total_tokens"));
        if (input == null || output == null || total == null || (long) input + output != total) return null;
        Integer cached = tokenCount(json.path("input_tokens_details").path("cached_tokens"));
        return new Usage(input, output, total, cached != null && cached <= input ? cached : null);
    }

    private static Integer tokenCount(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= 0 ? value.asInt() : null;
    }

    private static void append(StringBuilder target, String value) {
        if (!value.isBlank()) {
            if (!target.isEmpty()) target.append('\n');
            target.append(value);
        }
    }
}
