package io.interviewcodi.backend.practice.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI Responses API({@code POST /v1/responses})로 JSON 스키마에 맞는 결과 하나를 받는다.
 * <ul>
 * <li>{@code store=false}: 응답을 OpenAI에 보관하지 않는다(기본값은 30일 보관). 남용 감시 기록(최대 30일)은 별개다.</li>
 * <li>구조화 출력은 {@code text.format}의 strict JSON 스키마로 받는다. 거절(refusal)은 {@code REFUSAL}로 실패시킨다.</li>
 * <li>{@code usage.output_tokens}는 추론 토큰을 포함한다. 추론 토큰은 {@code thoughtTokens}로 따로 남긴다.</li>
 * </ul>
 */
public class OpenAiClient implements StructuredLlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Consumer<LlmCall> listener;

    public OpenAiClient(OpenAiProperties properties, Consumer<LlmCall> listener) {
        this(RestClient.builder().requestFactory(requestFactory(properties.timeout())), properties, listener);
    }

    OpenAiClient(RestClient.Builder builder, OpenAiProperties properties, Consumer<LlmCall> listener) {
        this.restClient = builder.baseUrl(properties.baseUri().toString()).build();
        this.properties = properties;
        this.listener = listener;
    }

    @Override
    public JsonNode generateJson(String purpose, String instructions, String input, String schemaName,
                                 Map<String, Object> schema, int maxOutputTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("reasoning", Map.of("effort", properties.reasoningEffort()));
        body.put("input", List.of(message("developer", instructions), message("user", input)));
        body.put("text", Map.of(
                "format", Map.of("type", "json_schema", "name", schemaName, "strict", true, "schema", schema),
                "verbosity", "low"));
        body.put("max_output_tokens", maxOutputTokens);
        body.put("store", false);

        long started = System.nanoTime();
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1/responses")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            // OpenAI가 돌려준 오류 메시지는 원인 파악에 필요해 남긴다. 우리 요청 본문은 남기지 않는다.
            String message = ex.getResponseBodyAsString();
            log.warn("OpenAI {} error {}: {}", purpose, ex.getStatusCode().value(),
                    message.length() > 500 ? message.substring(0, 500) : message);
            throw failed(purpose, started, "HTTP_" + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw failed(purpose, started, "TIMEOUT", ex);
        }

        String status = response.path("status").asString("unknown");
        record(purpose, started, response.path("usage"), status);
        if (!"completed".equals(status)) {
            throw new AiGenerationException("STATUS_" + status.toUpperCase(), null);
        }
        String text = outputText(response);
        try {
            return jsonMapper.readTree(text);
        } catch (RuntimeException ex) {
            throw new AiGenerationException("INVALID_OUTPUT", ex);
        }
    }

    @Override
    public String model() {
        return properties.model();
    }

    private static Map<String, Object> message(String role, String text) {
        return Map.of("role", role, "content", List.of(Map.of("type", "input_text", "text", text)));
    }

    /** {@code output} 항목의 {@code output_text}를 이어 붙인다. 거절이 있으면 실패시킨다. */
    private static String outputText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode item : response.path("output")) {
            for (JsonNode content : item.path("content")) {
                String type = content.path("type").asString("");
                if ("refusal".equals(type)) {
                    throw new AiGenerationException("REFUSAL", null);
                }
                if ("output_text".equals(type)) {
                    text.append(content.path("text").asString(""));
                }
            }
        }
        if (text.isEmpty()) {
            throw new AiGenerationException("EMPTY_OUTPUT", null);
        }
        return text.toString();
    }

    private AiGenerationException failed(String purpose, long started, String errorType, Exception cause) {
        record(purpose, started, null, errorType);
        return new AiGenerationException(errorType, cause);
    }

    private void record(String purpose, long started, JsonNode usage, String status) {
        LlmCall call = new LlmCall(properties.model(), purpose, (System.nanoTime() - started) / 1_000_000,
                usage == null ? 0 : usage.path("input_tokens").asInt(0),
                usage == null ? 0 : usage.path("output_tokens").asInt(0),
                usage == null ? 0 : usage.path("output_tokens_details").path("reasoning_tokens").asInt(0), status);
        log.info("OpenAI {} model={} status={} {}ms tokens in={} out={} reasoning={}", call.purpose(), call.model(),
                call.status(), call.latencyMillis(), call.inputTokens(), call.outputTokens(), call.thoughtTokens());
        if (listener != null) {
            listener.accept(call);
        }
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
