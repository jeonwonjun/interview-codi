package io.interviewcodi.backend.practice.client;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI Responses API 설정. 키는 {@code .env}의 {@code OPENAI_API_KEY}에서 읽는다.
 *
 * @param reasoningEffort 추론 강도(low 등). 추론 토큰은 출력 토큰으로 과금되므로 비용에 직접 영향을 준다
 * @param timeout         호출 한 번의 응답 제한 시간. 워커 임대(60초)보다 짧아야 한다
 */
@ConfigurationProperties("practice.ai.openai")
public record OpenAiProperties(String apiKey, String model, String reasoningEffort, URI baseUri, Duration timeout) {
}
