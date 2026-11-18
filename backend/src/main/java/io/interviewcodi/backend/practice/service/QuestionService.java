package io.interviewcodi.backend.practice.service;

import io.interviewcodi.backend.common.error.BusinessException;
import io.interviewcodi.backend.common.error.ErrorCode;
import io.interviewcodi.backend.practice.domain.AiRequestKind;
import io.interviewcodi.backend.practice.domain.AiRequestStatus;
import io.interviewcodi.backend.practice.domain.DiscardReason;
import io.interviewcodi.backend.practice.domain.ExperienceQuestion;
import io.interviewcodi.backend.practice.domain.PracticeStatus;
import io.interviewcodi.backend.practice.domain.QuestionSourceKind;
import io.interviewcodi.backend.practice.dto.AiRequestResponse;
import io.interviewcodi.backend.practice.dto.QuestionResponse;
import io.interviewcodi.backend.practice.repository.AiRequestRepository;
import io.interviewcodi.backend.practice.repository.AnswerRepository;
import io.interviewcodi.backend.practice.repository.PracticeRepository;
import io.interviewcodi.backend.practice.repository.QuestionRepository;
import io.interviewcodi.backend.practice.repository.QuestionRepository.CurrentQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다음 질문으로 넘어가는 두 가지 방법: 실제 답변에 근거한 꼬리질문(AI, 비동기)과 질문 넘기기(고정 질문, 즉시).
 * 둘 다 연습 행을 먼저 잠가, 꼬리질문 결과 반영(워커)과 한 줄로 선다.
 */
@Service
public class QuestionService {

    private final PracticeRepository practiceRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AiRequestRepository aiRequestRepository;

    QuestionService(PracticeRepository practiceRepository, QuestionRepository questionRepository,
                    AnswerRepository answerRepository, AiRequestRepository aiRequestRepository) {
        this.practiceRepository = practiceRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.aiRequestRepository = aiRequestRepository;
    }

    /**
     * 현재 질문에 대한 현재 버전의 답변을 근거로 꼬리질문 생성을 요청한다. 같은 버전의 요청이 처리 중이면 그것을 돌려준다.
     *
     * @return created=false면 이미 처리 중인 요청
     */
    @Transactional
    public FollowUpRequestResult requestFollowUp(long userId, long practiceId, Long answerRevisionId) {
        if (answerRevisionId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        long ownerPracticeId = answerRepository.findPracticeIdForOwner(answerRevisionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANSWER_REVISION_NOT_FOUND));
        if (ownerPracticeId != practiceId) {
            throw new BusinessException(ErrorCode.ANSWER_REVISION_NOT_FOUND);
        }
        lockInProgress(userId, practiceId, ErrorCode.PRACTICE_NOT_FOUND);
        if (!answerRepository.isCurrentRevision(answerRevisionId)) {
            throw new BusinessException(ErrorCode.NOT_LATEST_REVISION);
        }
        long sourceQuestionId = answerRepository.findRevisionSource(answerRevisionId).questionId();
        requireCurrentQuestion(practiceId, sourceQuestionId);

        Optional<AiRequestRepository.RequestRow> active =
                aiRequestRepository.findActive(answerRevisionId, AiRequestKind.FOLLOW_UP_QUESTION);
        if (active.isPresent()) {
            return new FollowUpRequestResult(
                    new AiRequestResponse(active.get().id(), active.get().status().name()), false);
        }
        long requestId = aiRequestRepository.insertPending(practiceId, AiRequestKind.FOLLOW_UP_QUESTION,
                answerRevisionId);
        return new FollowUpRequestResult(new AiRequestResponse(requestId, AiRequestStatus.PENDING.name()), true);
    }

    /**
     * 현재 질문을 넘기고 아직 쓰지 않은 고정 질문을 다음 질문으로 낸다. 답하지 않은 질문만 '넘긴 질문'으로 표시한다.
     * 이 질문을 근거로 대기 중인 꼬리질문 요청은 버린다(처리 중이면 워커가 반영할 때 버린다).
     */
    @Transactional
    public QuestionResponse skip(long userId, long questionId) {
        long practiceId = questionRepository.findPracticeIdForOwner(questionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        lockInProgress(userId, practiceId, ErrorCode.QUESTION_NOT_FOUND);
        requireCurrentQuestion(practiceId, questionId);
        ExperienceQuestion next = ExperienceQuestion.nextUnused(questionRepository.findUsedPresetKeys(practiceId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MORE_QUESTIONS));

        if (!questionRepository.hasAnswers(questionId)) {
            questionRepository.markSkipped(questionId);
        }
        aiRequestRepository.discardPending(practiceId, AiRequestKind.FOLLOW_UP_QUESTION,
                DiscardReason.QUESTION_CHANGED);
        int orderNo = questionRepository.nextOrderNo(practiceId);
        long nextId = questionRepository.insertPreset(practiceId, orderNo, next);
        return new QuestionResponse(nextId, orderNo, next.content(), QuestionSourceKind.EXPERIENCE_PRESET.name(),
                false, true, List.of());
    }

    private void lockInProgress(long userId, long practiceId, ErrorCode notFound) {
        PracticeStatus status = practiceRepository.lockForOwner(practiceId, userId)
                .orElseThrow(() -> new BusinessException(notFound));
        if (status != PracticeStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.PRACTICE_COMPLETED);
        }
    }

    private void requireCurrentQuestion(long practiceId, long questionId) {
        CurrentQuestion current = questionRepository.findCurrent(practiceId).orElseThrow();
        if (current.id() != questionId || current.skipped()) {
            throw new BusinessException(ErrorCode.NOT_CURRENT_QUESTION);
        }
    }

    public record FollowUpRequestResult(AiRequestResponse request, boolean created) {
    }
}
