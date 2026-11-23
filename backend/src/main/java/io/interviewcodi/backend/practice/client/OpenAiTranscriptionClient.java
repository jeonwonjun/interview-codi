package io.interviewcodi.backend.practice.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * OpenAI 전사({@code POST /v1/audio/transcriptions}). 2026-11-23 문서 기준.
 * <ul>
 * <li>이 엔드포인트의 입력은 학습에 쓰지 않고 남용 감시용 보관도 없다(OpenAI 데이터 통제 표).</li>
 * <li>{@code gpt-transcribe}는 언어를 {@code languages[]}로 받는다. 이전 모델은 {@code language}를 받는다.</li>
 * <li>webm·m4a·mp3·wav 등을 받고, 파일 하나 최대 25MB.</li>
 * <li>{@code prompt}로 녹음의 문맥을 줄 수 있다. 녹음과 같은 언어로 쓴다(2026-11-23 문서 기준, {@code gpt-transcribe} 지원).</li>
 * </ul>
 */
public class OpenAiTranscriptionClient implements SpeechToTextClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiTranscriptionClient(String apiKey, String model, URI baseUri, Duration timeout) {
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(timeout))
                .baseUrl(baseUri.toString())
                .build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public Transcription transcribe(AudioFile audio) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", filePart(audio));
        form.add("model", model);
        if (model.equals("gpt-transcribe")) {
            form.add("languages[]", "ko");
        } else {
            form.add("language", "ko");
        }
        form.add("response_format", "json");
        // 녹음 언어와 같은 언어의 자유 문맥. 짧고 머뭇거린 답을 엉뚱한 문장으로 채우는 것을 줄이려고 넘긴다.
        if (audio.context() != null && !audio.context().isBlank()) {
            form.add("prompt", audio.context());
        }

        long started = System.nanoTime();
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1/audio/transcriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new SpeechToTextException("HTTP_" + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw new SpeechToTextException("TIMEOUT", ex);
        }
        long latency = (System.nanoTime() - started) / 1_000_000;
        String text = response.path("text").asString("").strip();
        if (text.isEmpty()) {
            throw new SpeechToTextException("EMPTY_TEXT", null);
        }
        JsonNode usage = response.path("usage");
        Double seconds = "duration".equals(usage.path("type").asString("")) ? usage.path("seconds").asDouble() : null;
        return new Transcription(text, latency, seconds);
    }

    @Override
    public String name() {
        return "openai/" + model;
    }

    static HttpEntity<ByteArrayResource> filePart(AudioFile audio) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(audio.contentType()));
        ByteArrayResource resource = new ByteArrayResource(audio.bytes()) {
            @Override
            public String getFilename() {
                return audio.filename();
            }
        };
        return new HttpEntity<>(resource, headers);
    }

    static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
