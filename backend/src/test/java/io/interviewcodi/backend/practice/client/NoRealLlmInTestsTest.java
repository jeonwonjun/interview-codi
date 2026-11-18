package io.interviewcodi.backend.practice.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.interviewcodi.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/**
 * .env에 AI_PROVIDER가 있어도 테스트는 실제 LLM을 부르지 않는다. 2026-11-19 부하 측정이 .env의 설정을 그대로 받아
 * 실제 OpenAI를 236번 호출한 일이 있어 고정한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NoRealLlmInTestsTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("테스트에서는 가짜 생성기만 뜨고 실제 LLM 클라이언트는 만들지 않는다")
    void usesFakeGenerators() {
        assertThat(context.getBean(FeedbackGenerator.class)).isInstanceOf(FakeFeedbackGenerator.class);
        assertThat(context.getBean(FollowUpQuestionGenerator.class)).isInstanceOf(FakeFollowUpQuestionGenerator.class);
        assertThat(context.getBeanNamesForType(StructuredLlmClient.class)).isEmpty();
    }
}
