package io.interviewcodi.backend.practice.service;

import io.interviewcodi.backend.common.error.BusinessException;
import io.interviewcodi.backend.common.error.ErrorCode;
import io.interviewcodi.backend.practice.domain.AiRequestKind;
import io.interviewcodi.backend.practice.domain.AiRequestStatus;
import io.interviewcodi.backend.practice.domain.PracticeStatus;
import io.interviewcodi.backend.practice.dto.AiRequestResponse;
import io.interviewcodi.backend.practice.dto.AnswerAttemptResponse;
import io.interviewcodi.backend.practice.repository.AiRequestRepository;
import io.interviewcodi.backend.practice.repository.AiRequestRepository.RequestRow;
import io.interviewcodi.backend.practice.repository.AnswerRepository;
import io.interviewcodi.backend.practice.repository.AnswerRepository.AttemptRow;
import io.interviewcodi.backend.practice.repository.PracticeRepository;
import io.interviewcodi.backend.practice.repository.QuestionRepository;
import io.interviewcodi.backend.practice.repository.QuestionRepository.CurrentQuestion;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerService {

    private static final int MAX_ANSWER_LENGTH = 3_000;

    private final PracticeRepository practiceRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AiRequestRepository aiRequestRepository;

    AnswerService(PracticeRepository practiceRepository, QuestionRepository questionRepository,
                  AnswerRepository answerRepository, AiRequestRepository aiRequestRepository) {
        this.practiceRepository = practiceRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.aiRequestRepository = aiRequestRepository;
    }

    /**
     * 새 답변 시도와 그 버전 1, 피드백 요청을 한 트랜잭션에 저장한다. AI는 호출하지 않는다(워커가 처리).
     * <p>
     * 연습 행을 먼저 잠가 같은 연습의 다른 쓰기(마치기·삭제·다른 제출)와 한 줄로 세운다. 그래서 시도 번호
     * 계산과 같은 {@code clientRequestId}의 중복 확인 사이에 다른 제출이 끼지 않는다.
     */
    @Transactional
    public SubmitResult submit(long userId, long questionId, UUID clientRequestId, String content) {
        if (clientRequestId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String normalized = normalize(content);
        long practiceId = questionRepository.findPracticeIdForOwner(questionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        PracticeStatus status = practiceRepository.lockForOwner(practiceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));

        // 같은 제출을 다시 보낸 경우(응답을 못 받은 재전송 등): 새로 만들지 않고 처음 결과를 돌려준다.
        Optional<Long> existingAnswerId = answerRepository.findIdByClientRequestId(questionId, clientRequestId);
        if (existingAnswerId.isPresent()) {
            return new SubmitResult(load(practiceId, existingAnswerId.get()), false);
        }

        if (status != PracticeStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.PRACTICE_COMPLETED);
        }
        CurrentQuestion current = questionRepository.findCurrent(practiceId).orElseThrow();
        if (current.id() != questionId || current.skipped()) {
            throw new BusinessException(ErrorCode.NOT_CURRENT_QUESTION);
        }

        int attemptNo = answerRepository.nextAttemptNo(questionId);
        long answerId = answerRepository.insertAnswer(questionId, attemptNo, clientRequestId);
        long revisionId = answerRepository.insertRevision(answerId, 1, normalized);
        aiRequestRepository.insertPending(practiceId, AiRequestKind.FEEDBACK, revisionId);
        return new SubmitResult(load(practiceId, answerId), true);
    }

    /**
     * 같은 답변 시도의 문구를 교정해 새 버전을 저장하고, 새 버전의 피드백을 요청한다. 재답변(새 시도)으로 세지 않는다.
     * <p>
     * 낙관적 충돌: 화면이 보고 있던 버전 번호({@code baseRevisionNo})가 현재 버전과 같을 때만 그다음 번호로 저장한다.
     * 다른 탭에서 먼저 교정했다면 409로 알려 교정 내용이 조용히 덮어써지지 않게 한다. 연습 행 잠금이 같은 연습의
     * 교정을 한 줄로 세우고, {@code (answer_id, revision_no)} 유일 제약이 마지막으로 막는다.
     *
     * @return 본문이 현재 버전과 같으면 새 버전·요청 없이 현재 상태와 created=false
     */
    @Transactional
    public SubmitResult revise(long userId, long answerId, Integer baseRevisionNo, String content) {
        if (baseRevisionNo == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String normalized = normalize(content);
        long practiceId = answerRepository.findPracticeIdForOwnerByAnswer(answerId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANSWER_NOT_FOUND));
        PracticeStatus status = practiceRepository.lockForOwner(practiceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANSWER_NOT_FOUND));
        if (status != PracticeStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.PRACTICE_COMPLETED);
        }
        AttemptRow current = answerRepository.findAttemptWithCurrentRevision(answerId).orElseThrow();
        if (current.revisionNo() != baseRevisionNo) {
            throw new BusinessException(ErrorCode.REVISION_CONFLICT);
        }
        if (current.content().equals(normalized)) {
            return new SubmitResult(load(practiceId, answerId), false);
        }

        long revisionId;
        try {
            revisionId = answerRepository.insertRevision(answerId, baseRevisionNo + 1, normalized);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.REVISION_CONFLICT);
        }
        aiRequestRepository.discardPendingForOlderRevisions(answerId, revisionId);
        aiRequestRepository.insertPending(practiceId, AiRequestKind.FEEDBACK, revisionId);
        return new SubmitResult(load(practiceId, answerId), true);
    }

    /**
     * 현재 버전의 피드백을 다시 요청한다. 실패한 경우에만 새로 만들고, 처리 중이거나 성공했으면 그 요청을 돌려준다.
     * 같은 재요청이 동시에 들어와도 연습 행 잠금 아래에서 확인하고, 부분 유일 인덱스가 마지막으로 막는다.
     */
    @Transactional
    public FeedbackRequestResult requestFeedback(long userId, long revisionId) {
        long practiceId = answerRepository.findPracticeIdForOwner(revisionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANSWER_REVISION_NOT_FOUND));
        PracticeStatus status = practiceRepository.lockForOwner(practiceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANSWER_REVISION_NOT_FOUND));
        if (status != PracticeStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.PRACTICE_COMPLETED);
        }
        if (!answerRepository.isCurrentRevision(revisionId)) {
            throw new BusinessException(ErrorCode.NOT_LATEST_REVISION);
        }
        Optional<RequestRow> latest = aiRequestRepository.findLatestFeedbackRequest(revisionId);
        if (latest.isPresent() && latest.get().status() != AiRequestStatus.FAILED
                && latest.get().status() != AiRequestStatus.DISCARDED) {
            return new FeedbackRequestResult(toResponse(latest.get()), false);
        }
        long requestId = aiRequestRepository.insertPending(practiceId, AiRequestKind.FEEDBACK, revisionId);
        return new FeedbackRequestResult(new AiRequestResponse(requestId, AiRequestStatus.PENDING.name()), true);
    }

    private static AiRequestResponse toResponse(RequestRow row) {
        return new AiRequestResponse(row.id(), row.status().name());
    }

    private AnswerAttemptResponse load(long practiceId, long answerId) {
        AttemptRow attempt = answerRepository.findAttemptWithCurrentRevision(answerId).orElseThrow();
        Map<Long, RequestRow> requests = aiRequestRepository.findLatestFeedbackRequests(practiceId).stream()
                .collect(Collectors.toMap(RequestRow::answerRevisionId, Function.identity()));
        return PracticeService.toAttemptResponse(attempt, requests, Map.of());
    }

    /** 앞뒤 공백을 지우고 길이를 확인한다. 길이는 DB 제약과 같게 코드 포인트로 센다. */
    private static String normalize(String content) {
        String stripped = content == null ? "" : content.strip();
        int length = stripped.codePointCount(0, stripped.length());
        if (length < 1 || length > MAX_ANSWER_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ANSWER_CONTENT);
        }
        return stripped;
    }

    public record SubmitResult(AnswerAttemptResponse attempt, boolean created) {
    }

    public record FeedbackRequestResult(AiRequestResponse request, boolean created) {
    }
}
