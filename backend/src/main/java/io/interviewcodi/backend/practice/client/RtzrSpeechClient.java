package io.interviewcodi.backend.practice.client;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * 리턴제로(RTZR) 파일 STT. 2026-11-23 문서(developers.rtzr.ai) 기준.
 * <ol>
 * <li>{@code POST /v1/authenticate}(client_id, client_secret)로 6시간짜리 토큰을 받는다. 만료 30분 전에 새로 받는다.</li>
 * <li>{@code POST /v1/transcribe}(file, config)로 작업을 맡기고 {@code id}를 받는다.</li>
 * <li>{@code GET /v1/transcribe/{id}}를 {@code status=completed}가 될 때까지 다시 읽는다. 너무 자주 읽으면 429(A0003).</li>
 * </ol>
 * 받는 형식은 mp4·m4a·mp3·amr·flac·wav다. 브라우저 기본 녹음 형식(webm)은 받지 않아 서비스에 쓰려면 변환이 필요하다.
 * 결과는 요청 뒤 3일 보관된다. 학습 사용 여부는 문서에서 찾지 못했다.
 */
public class RtzrSpeechClient implements SpeechToTextClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String model;
    private final Duration pollInterval;
    private final Duration timeout;
    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public RtzrSpeechClient(String clientId, String clientSecret, String model, URI baseUri, Duration pollInterval,
                            Duration timeout) {
        this.restClient = RestClient.builder()
                .requestFactory(OpenAiTranscriptionClient.requestFactory(Duration.ofSeconds(30)))
                .baseUrl(baseUri.toString())
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.model = model;
        this.pollInterval = pollInterval;
        this.timeout = timeout;
    }

    @Override
    public Transcription transcribe(AudioFile audio) {
        long started = System.nanoTime();
        try {
            String id = submit(audio);
            return poll(id, started);
        } catch (RestClientResponseException ex) {
            throw new SpeechToTextException("HTTP_" + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw new SpeechToTextException("TIMEOUT", ex);
        }
    }

    @Override
    public String name() {
        return "rtzr/" + model;
    }

    private String submit(AudioFile audio) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", OpenAiTranscriptionClient.filePart(audio));
        // 숫자·영어 정규화와 군말 거르기는 기본값(켬)을 그대로 둔다. 서비스에서도 같은 설정으로 쓴다.
        form.add("config", "{\"model_name\":\"" + model + "\",\"language\":\"ko\"}");
        JsonNode response = restClient.post()
                .uri("/v1/transcribe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        return response.path("id").asString();
    }

    private Transcription poll(String id, long started) {
        long deadline = started + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            sleep();
            JsonNode response = restClient.get()
                    .uri("/v1/transcribe/{id}", id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                    .retrieve()
                    .body(JsonNode.class);
            String status = response.path("status").asString("");
            if (status.equals("failed")) {
                throw new SpeechToTextException("FAILED_" + response.path("error").path("code").asString(""), null);
            }
            if (status.equals("completed")) {
                return completed(response, started);
            }
        }
        throw new SpeechToTextException("TIMEOUT", null);
    }

    private static Transcription completed(JsonNode response, long started) {
        long latency = (System.nanoTime() - started) / 1_000_000;
        StringBuilder text = new StringBuilder();
        long endMillis = 0;
        for (JsonNode utterance : response.path("results").path("utterances")) {
            text.append(utterance.path("msg").asString("")).append(' ');
            endMillis = Math.max(endMillis,
                    utterance.path("start_at").asLong(0) + utterance.path("duration").asLong(0));
        }
        String joined = text.toString().strip();
        if (joined.isEmpty()) {
            throw new SpeechToTextException("EMPTY_TEXT", null);
        }
        return new Transcription(joined, latency, endMillis == 0 ? null : endMillis / 1000.0);
    }

    private synchronized String token() {
        if (token == null || Instant.now().isAfter(tokenExpiresAt.minus(Duration.ofMinutes(30)))) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            JsonNode response = restClient.post()
                    .uri("/v1/authenticate")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            token = response.path("access_token").asString();
            tokenExpiresAt = Instant.ofEpochSecond(response.path("expire_at").asLong());
        }
        return token;
    }

    private void sleep() {
        try {
            Thread.sleep(pollInterval);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SpeechToTextException("INTERRUPTED", ex);
        }
    }
}
