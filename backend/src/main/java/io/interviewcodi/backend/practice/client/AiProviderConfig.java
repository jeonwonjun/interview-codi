package io.interviewcodi.backend.practice.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code practice.ai.provider}로 LLM 제공자를 고른다.
 * <ul>
 * <li>{@code fake}(기본): 정해진 문구를 돌려주는 개발용 생성기({@link FakeFeedbackGenerator} 등). 이 설정은 쓰지 않는다.</li>
 * <li>{@code openai}, {@code gemini}: 실제 제공자. 피드백·꼬리질문 생성기는 제공자와 상관없이 {@link StructuredLlmClient}만 쓴다.</li>
 * </ul>
 * 제공자를 더하려면 {@link StructuredLlmClient} 구현과 설정 record를 만들고 {@link #llmClient}에 한 줄을 더한다.
 * 실제 사용자 답변을 받는 환경에서는 학습에 쓰지 않는 유료 등급 키만 쓴다(Gemini 무료 등급 입력은 제품 개선에 쓰인다).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${practice.ai.provider:fake}'.equals('fake')")
class AiProviderConfig {

    @Bean
    StructuredLlmClient llmClient(@Value("${practice.ai.provider}") String provider,
                                  ObjectProvider<OpenAiProperties> openAi, ObjectProvider<GeminiProperties> gemini) {
        return switch (provider) {
            case "openai" -> {
                OpenAiProperties properties = openAi.getObject();
                requireKey(properties.apiKey(), "OPENAI_API_KEY");
                yield new OpenAiClient(properties, null);
            }
            case "gemini" -> {
                GeminiProperties properties = gemini.getObject();
                requireKey(properties.apiKey(), "GEMINI_API_KEY");
                yield new GeminiClient(properties, null);
            }
            default -> throw new IllegalStateException("Unknown practice.ai.provider: " + provider);
        };
    }

    @Bean
    FeedbackGenerator llmFeedbackGenerator(StructuredLlmClient client) {
        return new LlmFeedbackGenerator(client);
    }

    @Bean
    FollowUpQuestionGenerator llmFollowUpQuestionGenerator(StructuredLlmClient client) {
        return new LlmFollowUpQuestionGenerator(client);
    }

    private static void requireKey(String key, String name) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("practice.ai.provider requires " + name);
        }
    }
}
