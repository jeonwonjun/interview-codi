package io.interviewcodi.backend.practice.worker;

import io.interviewcodi.backend.practice.service.AiRequestProcessingService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1초마다 AI 요청 큐를 비운다. 한 번에 {@code concurrency}개의 가상 스레드가 각자 꺼낼 것이 없을 때까지 처리한다.
 * 여러 스레드·서버가 함께 돌아도 {@code SKIP LOCKED} 덕분에 같은 요청을 동시에 처리하지 않는다.
 * <p>
 * 테스트에서는 {@code practice.ai.worker.enabled=false}로 끄고 {@link AiRequestProcessingService}를 직접 불러 순서를 제어한다.
 */
@Component
@ConditionalOnProperty(name = "practice.ai.worker.enabled", havingValue = "true", matchIfMissing = true)
class AiRequestWorker {

    private static final Logger log = LoggerFactory.getLogger(AiRequestWorker.class);

    private final AiRequestProcessingService processor;
    private final int concurrency;

    AiRequestWorker(AiRequestProcessingService processor, @Value("${practice.ai.worker.concurrency:2}") int concurrency) {
        this.processor = processor;
        this.concurrency = concurrency;
    }

    @Scheduled(fixedDelayString = "${practice.ai.worker.poll-interval:1s}")
    void drain() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                executor.submit(this::drainUntilEmpty);
            }
        }
    }

    private void drainUntilEmpty() {
        try {
            while (processor.processNext()) {
                // 꺼낼 것이 없을 때까지 계속 처리한다.
            }
        } catch (RuntimeException ex) {
            // DB 연결 실패 등. 다음 주기에 다시 시도한다.
            log.error("AI request worker stopped this round", ex);
        }
    }
}
