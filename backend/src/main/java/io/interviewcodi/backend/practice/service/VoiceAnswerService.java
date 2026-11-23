package io.interviewcodi.backend.practice.service;

import io.interviewcodi.backend.common.error.BusinessException;
import io.interviewcodi.backend.common.error.ErrorCode;
import io.interviewcodi.backend.practice.domain.AiRequestStatus;
import io.interviewcodi.backend.practice.domain.AudioFormat;
import io.interviewcodi.backend.practice.domain.InputMode;
import io.interviewcodi.backend.practice.domain.PracticeStatus;
import io.interviewcodi.backend.practice.dto.AiRequestResponse;
import io.interviewcodi.backend.practice.repository.AiRequestRepository;
import io.interviewcodi.backend.practice.repository.AiRequestRepository.TranscribeRow;
import io.interviewcodi.backend.practice.repository.AnswerRepository;
import io.interviewcodi.backend.practice.repository.PracticeRepository;
import io.interviewcodi.backend.practice.repository.QuestionRepository;
import io.interviewcodi.backend.practice.repository.QuestionRepository.CurrentQuestion;
import io.interviewcodi.backend.practice.repository.RecordingRepository;
import io.interviewcodi.backend.practice.repository.RecordingRepository.RecordingFileRow;
import io.interviewcodi.backend.practice.repository.RecordingRepository.RecordingOwner;
import io.interviewcodi.backend.practice.service.AnswerService.SubmitResult;
import io.interviewcodi.backend.practice.storage.RecordingStorage;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 음성 답변 올리기와 녹음 재생. 설계: docs/voice-practice-design.md 4절 2단계, 5-3.
 * <p>
 * 파일을 먼저 쓰고 DB 트랜잭션(시도·녹음·인식 요청)을 연다. 파일 쓰기가 실패하면 DB에 아무것도 남지 않고,
 * 트랜잭션이 실패하거나 같은 제출이 이미 있으면 방금 쓴 파일을 지운다. 그래서 DB가 가리키는 파일은 항상 있다.
 * 파일 쓰기를 트랜잭션 밖에 두어, 느린 디스크·저장소가 연습 행 잠금을 오래 붙잡지 않게 한다.
 */
@Service
public class VoiceAnswerService {

    private static final Logger log = LoggerFactory.getLogger(VoiceAnswerService.class);
    // 브라우저는 2초 미만 녹음을 올리지 않는다. 서버는 DB 제약과 같은 범위만 확인한다.
    private static final int MAX_DURATION_MS = 300_000;

    private final PracticeRepository practiceRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final RecordingRepository recordingRepository;
    private final AiRequestRepository aiRequestRepository;
    private final AnswerAttemptReader answerAttemptReader;
    private final RecordingStorage recordingStorage;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransaction;

    VoiceAnswerService(PracticeRepository practiceRepository, QuestionRepository questionRepository,
                       AnswerRepository answerRepository, RecordingRepository recordingRepository,
                       AiRequestRepository aiRequestRepository, AnswerAttemptReader answerAttemptReader,
                       RecordingStorage recordingStorage, PlatformTransactionManager transactionManager) {
        this.practiceRepository = practiceRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.recordingRepository = recordingRepository;
        this.aiRequestRepository = aiRequestRepository;
        this.answerAttemptReader = answerAttemptReader;
        this.recordingStorage = recordingStorage;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /**
     * 녹음을 저장하고 음성 시도·녹음·인식 요청을 만든다. 인식은 워커가 한다.
     * 같은 {@code clientRequestId}를 다시 보내면 파일을 쓰지 않고 처음 결과를 돌려준다.
     */
    public SubmitResult submit(long userId, long questionId, UUID clientRequestId, Integer durationMs,
                               String contentType, byte[] audio) {
        if (clientRequestId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Optional<SubmitResult> existing = readOnlyTransaction.execute(status -> findExisting(userId, questionId,
                clientRequestId));
        if (existing.isPresent()) {
            return existing.get();
        }
        if (durationMs == null || durationMs < 1 || durationMs > MAX_DURATION_MS || audio == null
                || audio.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_RECORDING);
        }
        AudioFormat format = AudioFormat.detect(contentType, audio)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RECORDING));

        // 추측할 수 없는 키. 사용자 ID로 나눠 두면 나중에 사용자 단위 정리·이전이 쉽다.
        String storageKey = "recordings/%d/%s.%s".formatted(userId, UUID.randomUUID(), format.extension());
        recordingStorage.save(storageKey, audio);
        SubmitResult result;
        try {
            result = transactionTemplate.execute(status -> saveAttempt(userId, questionId, clientRequestId,
                    durationMs, format, storageKey, audio.length));
        } catch (RuntimeException ex) {
            deleteOrphan(storageKey);
            throw ex;
        }
        if (!result.created()) {
            // 동시에 들어온 같은 제출이 먼저 저장했다. 이번 파일은 어디서도 가리키지 않는다.
            deleteOrphan(storageKey);
        }
        return result;
    }

    /** 본인의 삭제되지 않은 연습·시도·녹음일 때만 파일을 돌려준다. 아니면 모두 같은 404. */
    @Transactional(readOnly = true)
    public RecordingFile loadRecording(long userId, long answerId) {
        RecordingFileRow row = recordingRepository.findFileForOwner(answerId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORDING_NOT_FOUND));
        return new RecordingFile(recordingStorage.load(row.storageKey()), row.contentType(), row.sizeBytes());
    }

    /**
     * 인식이 실패했거나 버려진 녹음을 다시 글로 옮긴다. 처리 중이거나 이미 성공했으면 그 요청을 돌려준다.
     * 같은 재요청이 동시에 들어와도 연습 행 잠금 아래에서 확인하고, 녹음 기준 부분 유일 인덱스가 마지막으로 막는다.
     */
    @Transactional
    public TranscriptionRequestResult retryTranscription(long userId, long recordingId) {
        RecordingOwner owner = recordingRepository.findOwner(recordingId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORDING_NOT_FOUND));
        PracticeStatus status = practiceRepository.lockForOwner(owner.practiceId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORDING_NOT_FOUND));
        if (status != PracticeStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.PRACTICE_COMPLETED);
        }
        Optional<TranscribeRow> latest = aiRequestRepository.findLatestTranscribeRequest(recordingId);
        if (latest.isPresent() && latest.get().status() != AiRequestStatus.FAILED
                && latest.get().status() != AiRequestStatus.DISCARDED) {
            return new TranscriptionRequestResult(
                    new AiRequestResponse(latest.get().id(), latest.get().status().name()), false);
        }
        long requestId = aiRequestRepository.insertTranscribePending(owner.practiceId(), recordingId);
        return new TranscriptionRequestResult(new AiRequestResponse(requestId, AiRequestStatus.PENDING.name()), true);
    }

    private Optional<SubmitResult> findExisting(long userId, long questionId, UUID clientRequestId) {
        long practiceId = questionRepository.findPracticeIdForOwner(questionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        return answerRepository.findIdByClientRequestId(questionId, clientRequestId)
                .map(answerId -> new SubmitResult(answerAttemptReader.read(practiceId, answerId), false));
    }

    /** {@link AnswerService#submit}과 같은 순서로 확인한다. 연습 행을 잠가 같은 연습의 다른 쓰기와 한 줄로 세운다. */
    private SubmitResult saveAttempt(long userId, long questionId, UUID clientRequestId, int durationMs,
                                     AudioFormat format, String storageKey, long sizeBytes) {
        long practiceId = questionRepository.findPracticeIdForOwner(questionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        PracticeStatus status = practiceRepository.lockForOwner(practiceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));

        Optional<Long> existingAnswerId = answerRepository.findIdByClientRequestId(questionId, clientRequestId);
        if (existingAnswerId.isPresent()) {
            return new SubmitResult(answerAttemptReader.read(practiceId, existingAnswerId.get()), false);
        }
        if (status != PracticeStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.PRACTICE_COMPLETED);
        }
        CurrentQuestion current = questionRepository.findCurrent(practiceId).orElseThrow();
        if (current.id() != questionId || current.skipped()) {
            throw new BusinessException(ErrorCode.NOT_CURRENT_QUESTION);
        }
        if (aiRequestRepository.hasActiveTranscription(questionId)) {
            throw new BusinessException(ErrorCode.TRANSCRIPTION_IN_PROGRESS);
        }

        int attemptNo = answerRepository.nextAttemptNo(questionId);
        long answerId = answerRepository.insertAnswer(questionId, attemptNo, clientRequestId, InputMode.VOICE);
        long recordingId = recordingRepository.insert(answerId, storageKey, format.contentType(), durationMs,
                sizeBytes);
        aiRequestRepository.insertTranscribePending(practiceId, recordingId);
        return new SubmitResult(answerAttemptReader.read(practiceId, answerId), true);
    }

    private void deleteOrphan(String storageKey) {
        try {
            recordingStorage.deleteOrphan(storageKey);
        } catch (RuntimeException ex) {
            // 지우지 못해도 DB가 가리키지 않는 파일이라 접근할 수 없다. 정리 작업에서 다시 지운다.
            log.warn("Failed to delete orphan recording {}", storageKey, ex);
        }
    }

    public record TranscriptionRequestResult(AiRequestResponse request, boolean created) {
    }

    public record RecordingFile(Resource resource, String contentType, long sizeBytes) {
    }
}
