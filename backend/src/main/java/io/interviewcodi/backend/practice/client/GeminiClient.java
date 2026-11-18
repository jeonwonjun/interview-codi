package io.interviewcodi.backend.practice.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
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
 * Gemini Interactions API({@code POST /v1beta/interactions})로 JSON 스키마에 맞는 결과 하나를 받는다.
 * <ul>
 * <li>{@code store=false}: 요청·응답을 Google 서버에 남기지 않는다(기본값은 저장. 무료 1일, 유료 55일 보관).</li>
 * <li>구조화 출력({@code response_format})으로 JSON만 받는다. 스키마를 어긴 응답은 {@code INVALID_OUTPUT}으로 실패시킨다.</li>
 * <li>오류는 종류만 담은 {@link AiGenerationException}으로 바꾼다. 재시도 여부는 워커가 정한다.</li>
 * </ul>
 * 호출마다 지연과 토큰을 로그로 남기고, 평가처럼 따로 모아야 할 때는 {@code listener}로 받는다.
 */
public class GeminiClient implements StructuredLlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Consumer<LlmCall> listener;

    public GeminiClient(GeminiProperties properties, Consumer<LlmCall> listener) {
        this(RestClient.builder().requestFactory(requestFactory(properties.timeout())), properties, listener);
    }

    GeminiClient(RestClient.Builder builder, GeminiProperties properties, Consumer<LlmCall> listener) {
        this.restClient = builder.baseUrl(properties.baseUri().toString()).build();
        this.properties = properties;
        this.listener = listener;
    }

    /**
     * @param purpose 로그용 호출 목적
     * @return 스키마에 맞는 JSON
     * @throws AiGenerationException 호출 실패, 응답 미완료, JSON이 아닌 응답
     */
    @Override
    public JsonNode generateJson(String purpose, String systemInstruction, String input, String schemaName,
                                 Map<String, Object> schema, int maxOutputTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("system_instruction", systemInstruction);
        body.put("input", input);
        body.put("generation_config", Map.of(
                "thinking_level", properties.thinkingLevel(),
                "max_output_tokens", maxOutputTokens));
        body.put("response_format", Map.of("type", "text", "mime_type", "application/json", "schema", schema));
        body.put("store", false);

        long started = System.nanoTime();
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1beta/interactions")
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            // Google이 돌려준 오류 메시지는 원인 파악에 필요해 남긴다. 우리 요청 본문은 남기지 않는다.
            String message = ex.getResponseBodyAsString();
            log.warn("Gemini {} error {}: {}", purpose, ex.getStatusCode().value(),
                    message.length() > 500 ? message.substring(0, 500) : message);
            throw failed(purpose, started, "HTTP_" + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw failed(purpose, started, "TIMEOUT", ex);
        }

        JsonNode usage = response.path("usage");
        String status = response.path("status").asString("unknown");
        record(purpose, started, usage, status);
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

    /** {@code steps} 가운데 {@code model_output} 단계의 텍스트를 이어 붙인다. */
    private static String outputText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode step : response.path("steps")) {
            if (!"model_output".equals(step.path("type").asString(""))) {
                continue;
            }
            for (JsonNode content : step.path("content")) {
                if ("text".equals(content.path("type").asString(""))) {
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
                tokens(usage, "total_input_tokens"), tokens(usage, "total_output_tokens"),
                tokens(usage, "total_thought_tokens"), status);
        log.info("Gemini {} model={} status={} {}ms tokens in={} out={} thought={}", call.purpose(), call.model(),
                call.status(), call.latencyMillis(), call.inputTokens(), call.outputTokens(), call.thoughtTokens());
        if (listener != null) {
            listener.accept(call);
        }
    }

    @Override
    public String model() {
        return properties.model();
    }

    private static int tokens(JsonNode usage, String field) {
        return usage == null ? 0 : usage.path(field).asInt(0);
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return factory;
    }

}
