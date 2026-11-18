package io.interviewcodi.backend.practice.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import io.interviewcodi.backend.common.error.BusinessException;
import io.interviewcodi.backend.common.error.ErrorCode;
import io.interviewcodi.backend.practice.domain.AiRequestKind;
import io.interviewcodi.backend.practice.domain.DiscardReason;
import io.interviewcodi.backend.practice.domain.ExperienceQuestion;
import io.interviewcodi.backend.practice.domain.PracticeStatus;
import io.interviewcodi.backend.practice.domain.StartMode;
import io.interviewcodi.backend.practice.dto.AiRequestResponse;
import io.interviewcodi.backend.practice.dto.AnswerAttemptResponse;
import io.interviewcodi.backend.practice.dto.AnswerRevisionResponse;
import io.interviewcodi.backend.practice.dto.FeedbackResponse;
import io.interviewcodi.backend.practice.dto.PracticeListResponse;
import io.interviewcodi.backend.practice.dto.PracticeResponse;
import io.interviewcodi.backend.practice.dto.PracticeSummaryResponse;
import io.interviewcodi.backend.practice.dto.QuestionResponse;
import io.interviewcodi.backend.practice.repository.AiRequestRepository;
import io.interviewcodi.backend.practice.repository.AiRequestRepository.RequestRow;
import io.interviewcodi.backend.practice.repository.AnswerRepository;
import io.interviewcodi.backend.practice.repository.AnswerRepository.AttemptRow;
import io.interviewcodi.backend.practice.repository.FeedbackRepository;
import io.interviewcodi.backend.practice.repository.FeedbackRepository.FeedbackRow;
import io.interviewcodi.backend.practice.repository.PracticeRepository;
import io.interviewcodi.backend.practice.repository.PracticeRepository.PracticeRow;
import io.interviewcodi.backend.practice.repository.QuestionRepository;
import io.interviewcodi.backend.practice.repository.QuestionRepository.QuestionRow;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연습의 생성·조회·마치기·삭제. 모든 메서드는 세션에서 꺼낸 사용자 ID를 받아 소유자를 확인한다.
 */
@Service
public class PracticeService {

    private final PracticeRepository practiceRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AiRequestRepository aiRequestRepository;
    private final FeedbackRepository feedbackRepository;

    PracticeService(PracticeRepository practiceRepository, QuestionRepository questionRepository,
                    AnswerRepository answerRepository, AiRequestRepository aiRequestRepository,
                    FeedbackRepository feedbackRepository) {
        this.practiceRepository = practiceRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.aiRequestRepository = aiRequestRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /** 연습과 첫 고정 질문을 함께 저장한다. 질문 없는 연습이 생기지 않는다. */
    @Transactional
    public long create(long userId, String startMode) {
        if (!StartMode.EXPERIENCE.name().equals(startMode)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_START_MODE);
        }
        long practiceId = practiceRepository.insert(userId, StartMode.EXPERIENCE);
        questionRepository.insertPreset(practiceId, 1, ExperienceQuestion.first());
        return practiceId;
    }

    @Transactional(readOnly = true)
    public PracticeListResponse list(long userId) {
        List<PracticeSummaryResponse> items = practiceRepository.findSummaries(userId).stream()
                .map(row -> new PracticeSummaryResponse(row.id(), row.startMode().name(), row.status().name(),
                        row.createdAt(), row.completedAt(), row.questionCount(),
                        row.status() == PracticeStatus.IN_PROGRESS))
                .toList();
        return new PracticeListResponse(items);
    }

    /** 여러 조회를 하나의 스냅샷에서 읽도록 읽기 트랜잭션으로 묶는다. */
    @Transactional(readOnly = true)
    public PracticeResponse get(long userId, long practiceId) {
        PracticeRow practice = practiceRepository.findForOwner(practiceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRACTICE_NOT_FOUND));
        List<QuestionRow> questions = questionRepository.findByPractice(practiceId);
        Map<Long, List<AttemptRow>> attemptsByQuestion = answerRepository.findAttemptsWithCurrentRevision(practiceId)
                .stream()
                .collect(groupingBy(AttemptRow::questionId));
        Map<Long, RequestRow> requestByRevision = aiRequestRepository.findLatestFeedbackRequests(practiceId).stream()
                .collect(toMap(RequestRow::answerRevisionId, Function.identity()));
        Map<Long, FeedbackRow> feedbackByRevision = feedbackRepository.findByPractice(practiceId).stream()
                .collect(toMap(FeedbackRow::answerRevisionId, Function.identity()));

        long currentQuestionId = questions.getLast().id();
        List<QuestionResponse> questionResponses = questions.stream()
                .map(question -> new QuestionResponse(question.id(), question.orderNo(), question.content(),
                        question.sourceKind().name(), question.skipped(), question.id() == currentQuestionId,
                        attemptsByQuestion.getOrDefault(question.id(), List.of()).stream()
                                .sorted(Comparator.comparingInt(AttemptRow::attemptNo))
                                .map(attempt -> toAttemptResponse(attempt, requestByRevision, feedbackByRevision))
                                .toList()))
                .toList();

        boolean canProceed = practice.status() == PracticeStatus.IN_PROGRESS;
        return new PracticeResponse(practice.id(), practice.startMode().name(), practice.status().name(),
                practice.createdAt(), practice.completedAt(), canProceed,
                canProceed ? null : practice.status().name(), questionResponses,
                aiRequestRepository.findActiveFollowUp(practiceId)
                        .map(request -> new AiRequestResponse(request.id(), request.status().name()))
                        .orElse(null));
    }

    /**
     * 연습을 마친다. 이미 마쳤으면 완료 시각을 바꾸지 않는다.
     * 꼬리질문 요청은 버리고, 피드백 요청은 그대로 두어 완료 후에도 결과를 저장한다.
     */
    @Transactional
    public void complete(long userId, long practiceId) {
        PracticeStatus status = practiceRepository.lockForOwner(practiceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRACTICE_NOT_FOUND));
        if (status == PracticeStatus.COMPLETED) {
            return;
        }
        practiceRepository.complete(practiceId);
        aiRequestRepository.discardPending(practiceId, AiRequestKind.FOLLOW_UP_QUESTION,
                DiscardReason.PRACTICE_COMPLETED);
    }

    /** 논리적 삭제. 아직 꺼내지 않은 AI 요청은 버린다. 처리 중인 요청은 워커가 결과를 반영할 때 버린다. */
    @Transactional
    public void delete(long userId, long practiceId) {
        practiceRepository.lockForOwner(practiceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRACTICE_NOT_FOUND));
        practiceRepository.softDelete(practiceId);
        aiRequestRepository.discardAllPending(practiceId, DiscardReason.PRACTICE_DELETED);
    }

    public static AnswerAttemptResponse toAttemptResponse(AttemptRow attempt, Map<Long, RequestRow> requestByRevision,
                                                   Map<Long, FeedbackRow> feedbackByRevision) {
        RequestRow request = requestByRevision.get(attempt.revisionId());
        FeedbackRow feedback = feedbackByRevision.get(attempt.revisionId());
        return new AnswerAttemptResponse(attempt.id(), attempt.questionId(), attempt.attemptNo(),
                new AnswerRevisionResponse(attempt.revisionId(), attempt.revisionNo(), attempt.content()),
                feedback == null ? null : new FeedbackResponse(feedback.id(), feedback.answerRevisionId(),
                        feedback.observation(), feedback.suggestion(), feedback.suggestionFocus(),
                        feedback.previousReview()),
                request == null ? null : new AiRequestResponse(request.id(), request.status().name()));
    }
}
