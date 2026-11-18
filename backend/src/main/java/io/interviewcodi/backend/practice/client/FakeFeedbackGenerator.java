package io.interviewcodi.backend.practice.client;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 실제 AI 제공자를 연결하기 전까지 쓰는 개발용 생성기. 답변 내용을 평가하지 않고 정해진 문구를 돌려준다.
 * 이 결과로 질문·피드백의 품질을 확인했다고 보지 않는다.
 * <p>
 * 부하 측정에서는 {@code practice.ai.fake.delay-min}·{@code delay-max}로 실제 AI처럼 응답 시간을 흉내 낸다
 * (구간 안에서 균등 분포). 기본값은 지연 없음.
 */
@Component
class FakeFeedbackGenerator implements FeedbackGenerator {

    static final String NAME = "fake-v1";

    private final Duration delayMin;
    private final Duration delayMax;

    FakeFeedbackGenerator(@Value("${practice.ai.fake.delay-min:0ms}") Duration delayMin,
                          @Value("${practice.ai.fake.delay-max:0ms}") Duration delayMax) {
        this.delayMin = delayMin;
        this.delayMax = delayMax;
    }

    @Override
    public GeneratedFeedback generate(FeedbackInput input) {
        simulateLatency();
        String previousReview = input.previousSuggestion() == null
                ? null
                : "(개발용) 지난번 개선점을 반영했는지 실제 AI가 연결되면 확인해 드릴게요.";
        return new GeneratedFeedback(
                "(개발용 피드백) 답변을 잘 받았어요. 질문에 대한 핵심 답이 앞부분에 있는지 살펴보세요.",
                "본인이 직접 한 일을 한 가지 구체적으로 덧붙여 보세요.",
                "EVIDENCE",
                previousReview,
                NAME);
    }

    private void simulateLatency() {
        long min = delayMin.toMillis();
        long max = Math.max(min, delayMax.toMillis());
        if (max == 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(min, max + 1));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiGenerationException("INTERRUPTED", ex);
        }
    }
}
