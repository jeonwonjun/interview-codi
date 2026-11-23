package io.interviewcodi.backend.practice.worker;

import io.interviewcodi.backend.practice.domain.AiRequestKind;
import io.interviewcodi.backend.practice.service.AiRequestProcessingService;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * AI 요청 큐를 처리하는 슬롯 {@code concurrency}개. 슬롯마다 가상 스레드 하나가 계속 돌며, 꺼낼 것이 없을 때만
 * {@code poll-interval}만큼 쉰다. 여러 슬롯·서버가 함께 돌아도 {@code SKIP LOCKED} 덕분에 같은 요청을 동시에
 * 처리하지 않는다.
 * <p>
 * {@code transcribe-slots}만큼은 인식(TRANSCRIBE)만 꺼내는 슬롯을 따로 둔다. 공용 슬롯은 모든 종류를 꺼내므로,
 * 인식 전용 슬롯은 인식에 떼어 둔 최소 몫이다(설계 5-2의 B안).
 * <p>
 * 이전 구현(2026-11-19 기준선 측정에서 발견)은 주기마다 슬롯을 띄우고 큐가 비면 끝냈다. 다음 주기는 앞선 슬롯이
 * 모두 끝나야 시작했기 때문에, 요청이 한 건씩 들어오면 한 슬롯만 남아 큐를 비웠고 실제 동시 처리는 1이었다.
 * <p>
 * 테스트에서는 {@code practice.ai.worker.enabled=false}로 끄고 {@link AiRequestProcessingService}를 직접 불러
 * 순서를 제어한다.
 */
@Component
@ConditionalOnProperty(name = "practice.ai.worker.enabled", havingValue = "true", matchIfMissing = true)
class AiRequestWorker implements SmartLifecycle {

    /** 종료할 때 처리 중인 AI 호출을 기다리는 최대 시간. 넘으면 중단하고, 그 요청은 임대가 끝난 뒤 다시 처리된다. */
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(AiRequestWorker.class);
    private static final Set<AiRequestKind> ALL_KINDS = EnumSet.allOf(AiRequestKind.class);
    private static final Set<AiRequestKind> TRANSCRIBE_ONLY = EnumSet.of(AiRequestKind.TRANSCRIBE);

    private final AiRequestProcessingService processor;
    private final int concurrency;
    private final int transcribeSlots;
    private final Duration pollInterval;
    private final AtomicBoolean running = new AtomicBoolean();
    private ExecutorService slots;

    AiRequestWorker(AiRequestProcessingService processor,
                    @Value("${practice.ai.worker.concurrency:10}") int concurrency,
                    @Value("${practice.ai.worker.transcribe-slots:0}") int transcribeSlots,
                    @Value("${practice.ai.worker.poll-interval:1s}") Duration pollInterval) {
        this.processor = processor;
        this.concurrency = concurrency;
        this.transcribeSlots = transcribeSlots;
        this.pollInterval = pollInterval;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        slots = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < concurrency; i++) {
            slots.submit(() -> runSlot(ALL_KINDS));
        }
        for (int i = 0; i < transcribeSlots; i++) {
            slots.submit(() -> runSlot(TRANSCRIBE_ONLY));
        }
        log.info("AI request worker started with {} shared slots and {} transcribe-only slots", concurrency,
                transcribeSlots);
    }

    private void runSlot(Set<AiRequestKind> kinds) {
        while (running.get()) {
            try {
                if (!processor.processNext(kinds)) {
                    Thread.sleep(pollInterval);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                // DB 연결 실패 등. 잠시 쉬고 다시 시도한다.
                log.error("AI request worker slot failed; retrying after {}", pollInterval, ex);
                sleepQuietly();
            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(pollInterval);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        slots.shutdown();
        try {
            if (!slots.awaitTermination(SHUTDOWN_GRACE.toSeconds(), TimeUnit.SECONDS)) {
                slots.shutdownNow();
            }
        } catch (InterruptedException ex) {
            slots.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
