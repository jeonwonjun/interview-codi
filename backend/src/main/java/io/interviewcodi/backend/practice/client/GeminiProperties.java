package io.interviewcodi.backend.practice.client;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gemini Interactions API 설정. 키는 {@code .env}의 {@code GEMINI_API_KEY}에서 읽는다.
 *
 * @param thinkingLevel 생각 깊이(minimal·low·medium·high). 출력 단가에 생각 토큰이 포함되므로 비용에 직접 영향을 준다
 * @param timeout       호출 한 번의 응답 제한 시간. 워커 임대(60초)보다 짧아야 한다
 */
@ConfigurationProperties("practice.ai.gemini")
public record GeminiProperties(String apiKey, String model, String thinkingLevel, URI baseUri, Duration timeout) {
}
