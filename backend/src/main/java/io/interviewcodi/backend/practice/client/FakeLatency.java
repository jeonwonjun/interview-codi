package io.interviewcodi.backend.practice.client;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 가짜 생성기의 응답 지연. 부하 측정에서 {@code practice.ai.fake.delay-min}·{@code delay-max}로 실제 AI처럼
 * 응답 시간을 흉내 낸다(구간 안에서 균등 분포). 기본값은 지연 없음.
 */
@Component
class FakeLatency {

    private final Duration min;
    private final Duration max;

    FakeLatency(@Value("${practice.ai.fake.delay-min:0ms}") Duration min,
                @Value("${practice.ai.fake.delay-max:0ms}") Duration max) {
        this.min = min;
        this.max = max;
    }

    void simulate() {
        long minMillis = min.toMillis();
        long maxMillis = Math.max(minMillis, max.toMillis());
        if (maxMillis == 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiGenerationException("INTERRUPTED", ex);
        }
    }
}
