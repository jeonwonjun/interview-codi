package io.interviewcodi.backend.practice.client;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code practice.stt.provider}로 음성인식 제공자를 고른다. {@code fake}(기본)면 {@link FakeSpeechToTextClient}가 뜨고
 * 이 설정은 쓰지 않는다. 지금 실제 제공자는 OpenAI {@code gpt-transcribe}다(결정 기록 0009).
 * 리턴제로·CLOVA 클라이언트는 평가 실행기에서만 쓴다. 이들은 webm을 받지 않아 서비스에 붙이려면 형식 변환이 필요하다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${practice.stt.provider:fake}'.equals('fake')")
class SpeechToTextConfig {

    @Bean
    SpeechToTextClient speechToTextClient(@Value("${practice.stt.provider}") String provider,
                                          @Value("${practice.stt.openai.api-key:}") String apiKey,
                                          @Value("${practice.stt.openai.model}") String model,
                                          @Value("${practice.stt.openai.base-uri}") URI baseUri,
                                          @Value("${practice.stt.openai.timeout}") Duration timeout) {
        if (!provider.equals("openai")) {
            throw new IllegalStateException("Unknown practice.stt.provider: " + provider);
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("practice.stt.provider=openai requires OPENAI_API_KEY");
        }
        return new OpenAiTranscriptionClient(apiKey, model, baseUri, timeout);
    }
}
