package io.interviewcodi.backend.practice.service;

import io.interviewcodi.backend.practice.client.AiGenerationException;
import io.interviewcodi.backend.practice.client.FeedbackGenerator;
import io.interviewcodi.backend.practice.client.FeedbackGenerator.GeneratedFeedback;
import io.interviewcodi.backend.practice.domain.AiRequestKind;
import io.interviewcodi.backend.practice.repository.AiRequestRepository.ClaimedRequest;
import io.interviewcodi.backend.practice.service.AiRequestQueueService.Claim;
import io.interviewcodi.backend.practice.service.AiRequestQueueService.Outcome;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 요청 하나를 처리한다: 꺼내기(트랜잭션) → AI 호출(트랜잭션 밖) → 반영 또는 실패 기록(트랜잭션).
 * <p>
 * 이 클래스는 트랜잭션을 열지 않는다. AI 호출 동안 DB 연결과 잠금을 붙잡지 않기 위해서다.
 * 각 단계의 트랜잭션은 {@link AiRequestQueueService}가 연다(같은 클래스 안 호출은 프록시를 거치지 않아
 * {@code @Transactional}이 적용되지 않으므로 클래스를 나눴다).
 */
@Service
public class AiRequestProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AiRequestProcessingService.class);

    private final AiRequestQueueService queue;
    private final FeedbackGenerator feedbackGenerator;

    AiRequestProcessingService(AiRequestQueueService queue, FeedbackGenerator feedbackGenerator) {
        this.queue = queue;
        this.feedbackGenerator = feedbackGenerator;
    }

    /**
     * @return 요청 하나를 꺼냈으면 true(결과와 상관없이). 꺼낼 것이 없으면 false
     */
    public boolean processNext() {
        Optional<Claim> claim = queue.claim();
        if (claim.isEmpty()) {
            return false;
        }
        ClaimedRequest request = claim.get().request();
        if (request == null) {
            return true;
        }
        if (request.kind() != AiRequestKind.FEEDBACK) {
            // 꼬리질문 생성은 4단계에서 연결한다. 그 전에 쌓인 요청은 실패로 남긴다.
            queue.fail(request, "UNSUPPORTED_KIND");
            return true;
        }
        Outcome outcome;
        try {
            GeneratedFeedback feedback = feedbackGenerator.generate(queue.loadFeedbackInput(request));
            outcome = queue.applyFeedback(request, feedback);
        } catch (AiGenerationException ex) {
            outcome = queue.fail(request, ex.getErrorType());
        } catch (RuntimeException ex) {
            // 예상하지 못한 오류도 같은 재시도 규칙을 따른다. 원인은 로그로만 남긴다.
            log.error("Unexpected error while processing AI request {}", request.id(), ex);
            outcome = queue.fail(request, ex.getClass().getSimpleName());
        }
        log.info("AI request {} ({}, attempt {}) -> {}", request.id(), request.kind(), request.attemptCount(),
                outcome);
        return true;
    }
}
