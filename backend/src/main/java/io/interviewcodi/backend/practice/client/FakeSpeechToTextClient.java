package io.interviewcodi.backend.practice.client;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 개발·테스트용 인식기. 녹음 내용과 상관없이 정해진 문구를 돌려준다.
 * 부하 측정에서는 {@code practice.stt.fake.delay-min}·{@code delay-max}로 실제 인식처럼 응답 시간을 흉내 낸다.
 */
@Component
@ConditionalOnProperty(name = "practice.stt.provider", havingValue = "fake", matchIfMissing = true)
class FakeSpeechToTextClient implements SpeechToTextClient {

    static final String TEXT = "(개발용 인식 문구) 제가 맡은 기능은 예약입니다. 동시에 요청이 들어와도 한 번만 저장되게 만들었습니다.";

    private final Duration min;
    private final Duration max;

    FakeSpeechToTextClient(@Value("${practice.stt.fake.delay-min:0ms}") Duration min,
                           @Value("${practice.stt.fake.delay-max:0ms}") Duration max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public Transcription transcribe(AudioFile audio) {
        long started = System.nanoTime();
        long minMillis = min.toMillis();
        long maxMillis = Math.max(minMillis, max.toMillis());
        if (maxMillis > 0) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SpeechToTextException("INTERRUPTED", ex);
            }
        }
        return new Transcription(TEXT, (System.nanoTime() - started) / 1_000_000, null);
    }

    @Override
    public String name() {
        return "fake";
    }
}
