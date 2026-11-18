package io.interviewcodi.backend.practice.service;

import io.interviewcodi.backend.practice.client.FeedbackGenerator.FeedbackInput;
import io.interviewcodi.backend.practice.client.FeedbackGenerator.GeneratedFeedback;
import io.interviewcodi.backend.practice.domain.DiscardReason;
import io.interviewcodi.backend.practice.repository.AiRequestRepository;
import io.interviewcodi.backend.practice.repository.AiRequestRepository.ClaimedRequest;
import io.interviewcodi.backend.practice.repository.AnswerRepository;
import io.interviewcodi.backend.practice.repository.AnswerRepository.FeedbackSource;
import io.interviewcodi.backend.practice.repository.FeedbackRepository;
import io.interviewcodi.backend.practice.repository.PracticeRepository;
import io.interviewcodi.backend.practice.repository.PracticeRepository.LockedPractice;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 요청 큐의 짧은 트랜잭션들. AI 호출은 여기서 하지 않는다({@link AiRequestProcessingService}가 트랜잭션 밖에서 한다).
 * <p>
 * 결과 반영은 사용자 요청과 같은 순서로 잠근다: 연습 행 → 요청 행. 그래서 답변 교정·연습 마치기·삭제와
 * 결과 반영이 서로를 기다릴 수는 있어도 교착하지 않는다.
 */
@Service
public class AiRequestQueueService {

    /** 꺼낸 요청의 임대 시간. AI 호출 제한 시간보다 길어야 한다. */
    static final Duration LEASE = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 3;
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(5);

    private final AiRequestRepository aiRequestRepository;
    private final PracticeRepository practiceRepository;
    private final AnswerRepository answerRepository;
    private final FeedbackRepository feedbackRepository;

    AiRequestQueueService(AiRequestRepository aiRequestRepository, PracticeRepository practiceRepository,
                          AnswerRepository answerRepository, FeedbackRepository feedbackRepository) {
        this.aiRequestRepository = aiRequestRepository;
        this.practiceRepository = practiceRepository;
        this.answerRepository = answerRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * 요청 하나를 꺼낸다. 꺼낼 것이 없으면 빈 값.
     * 임대가 끝나 다시 꺼낸 요청이 이미 최대 횟수를 넘었다면 FAILED로 끝내고, 이번에는 처리하지 않는다.
     */
    @Transactional
    public Optional<Claim> claim() {
        Optional<ClaimedRequest> claimed = aiRequestRepository.claimNext(UUID.randomUUID(), LEASE);
        if (claimed.isEmpty()) {
            return Optional.empty();
        }
        ClaimedRequest request = claimed.get();
        if (request.attemptCount() > MAX_ATTEMPTS) {
            aiRequestRepository.markFailed(request.id(), "LEASE_EXPIRED");
            return Optional.of(Claim.skipped());
        }
        return Optional.of(Claim.of(request));
    }

    @Transactional(readOnly = true)
    public FeedbackInput loadFeedbackInput(ClaimedRequest request) {
        FeedbackSource source = answerRepository.findFeedbackSource(request.answerRevisionId());
        return new FeedbackInput(source.question(), source.answer(), source.previousSuggestion());
    }

    /**
     * 생성한 피드백을 반영한다. 그 사이 결과가 무의미해졌으면 버리고 사유를 남긴다.
     * <ul>
     * <li>연습이 삭제됨 → PRACTICE_DELETED</li>
     * <li>근거 버전이 교정되어 현재 버전이 아님 → SUPERSEDED</li>
     * <li>연습을 마친 것은 막지 않는다. 마치기 전에 접수한 피드백은 기록에 저장한다.</li>
     * </ul>
     *
     * @return 반영 결과. 임대를 잃었으면(다른 워커가 다시 꺼냄) 아무것도 하지 않는다
     */
    @Transactional
    public Outcome applyFeedback(ClaimedRequest request, GeneratedFeedback feedback) {
        LockedPractice practice = practiceRepository.lockById(request.practiceId()).orElseThrow();
        if (!aiRequestRepository.lockIfStillLeased(request.id(), request.leaseToken())) {
            return Outcome.LEASE_LOST;
        }
        if (practice.deleted()) {
            aiRequestRepository.markDiscarded(request.id(), DiscardReason.PRACTICE_DELETED);
            return Outcome.DISCARDED;
        }
        if (!answerRepository.isCurrentRevision(request.answerRevisionId())) {
            aiRequestRepository.markDiscarded(request.id(), DiscardReason.SUPERSEDED);
            return Outcome.DISCARDED;
        }
        feedbackRepository.insert(request.id(), request.answerRevisionId(), feedback.observation(),
                feedback.suggestion(), feedback.suggestionFocus(), feedback.previousReview(), feedback.generator());
        aiRequestRepository.markSucceeded(request.id());
        return Outcome.SUCCEEDED;
    }

    /**
     * 생성 실패. {@link #MAX_ATTEMPTS}번째 실패면 FAILED(사용자가 재요청할 수 있음), 아니면 간격을 늘려 다시 시도한다.
     */
    @Transactional
    public Outcome fail(ClaimedRequest request, String errorType) {
        practiceRepository.lockById(request.practiceId()).orElseThrow();
        if (!aiRequestRepository.lockIfStillLeased(request.id(), request.leaseToken())) {
            return Outcome.LEASE_LOST;
        }
        if (request.attemptCount() >= MAX_ATTEMPTS) {
            aiRequestRepository.markFailed(request.id(), errorType);
            return Outcome.FAILED;
        }
        Duration delay = BASE_RETRY_DELAY.multipliedBy(1L << (request.attemptCount() - 1));
        aiRequestRepository.retryLater(request.id(), delay, errorType);
        return Outcome.RETRY_SCHEDULED;
    }

    /** @param request 처리할 요청. 꺼냈지만 처리하지 않고 끝낸 경우 null */
    public record Claim(ClaimedRequest request) {

        static Claim of(ClaimedRequest request) {
            return new Claim(request);
        }

        static Claim skipped() {
            return new Claim(null);
        }
    }

    public enum Outcome {
        SUCCEEDED,
        DISCARDED,
        RETRY_SCHEDULED,
        FAILED,
        LEASE_LOST
    }
}
