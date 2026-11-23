package io.interviewcodi.backend.practice.client;

import java.net.URI;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * 네이버 CLOVA Speech 장문 인식, 로컬 파일 동기 방식({@code POST {Invoke URL}/recognizer/upload}). 2026-11-23 문서 기준.
 * <ul>
 * <li>Invoke URL은 도메인마다 다르다(빌더 > 설정 > 연동 정보). 키는 {@code X-CLOVASPEECH-API-KEY} 헤더로 보낸다.</li>
 * <li>{@code media}(파일)와 {@code params}(JSON 문자열)를 보낸다. 동기({@code completion=sync})면 응답의 {@code text}가 전체 문구다.</li>
 * <li>받는 형식: MP3·AAC·AC3·OGG·FLAC·WAV·M4A. 목록에 webm은 없다. 보관·학습 사용 여부는 문서에서 찾지 못했다.</li>
 * </ul>
 * <p>
 * 문맥({@link AudioFile#context()})은 쓰지 않는다. 평가 실행기에서만 쓰는 클라이언트라 지원 여부를 확인하지 않았다.
 */
public class ClovaSpeechClient implements SpeechToTextClient {

    private final RestClient restClient;
    private final String secretKey;

    public ClovaSpeechClient(URI invokeUrl, String secretKey, Duration timeout) {
        this.restClient = RestClient.builder()
                .requestFactory(OpenAiTranscriptionClient.requestFactory(timeout))
                .baseUrl(invokeUrl.toString())
                .build();
        this.secretKey = secretKey;
    }

    @Override
    public Transcription transcribe(AudioFile audio) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("media", OpenAiTranscriptionClient.filePart(audio));
        form.add("params", "{\"language\":\"ko-KR\",\"completion\":\"sync\",\"fullText\":true,"
                + "\"diarization\":{\"enable\":false}}");
        form.add("type", "application/json");

        long started = System.nanoTime();
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/recognizer/upload")
                    .header("X-CLOVASPEECH-API-KEY", secretKey)
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
        if (!"COMPLETED".equals(response.path("result").asString(""))) {
            throw new SpeechToTextException("STATUS_" + response.path("result").asString("UNKNOWN"), null);
        }
        String text = response.path("text").asString("").strip();
        if (text.isEmpty()) {
            throw new SpeechToTextException("EMPTY_TEXT", null);
        }
        long endMillis = 0;
        for (JsonNode segment : response.path("segments")) {
            endMillis = Math.max(endMillis, segment.path("end").asLong(0));
        }
        return new Transcription(text, latency, endMillis == 0 ? null : endMillis / 1000.0);
    }

    @Override
    public String name() {
        return "clova/longsentence";
    }
}
