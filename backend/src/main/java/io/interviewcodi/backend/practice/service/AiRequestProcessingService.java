package io.interviewcodi.backend.practice.service;

import io.interviewcodi.backend.practice.client.AiGenerationException;
import io.interviewcodi.backend.practice.client.FeedbackGenerator;
import io.interviewcodi.backend.practice.client.FeedbackGenerator.GeneratedFeedback;
import io.interviewcodi.backend.practice.client.FollowUpQuestionGenerator;
import io.interviewcodi.backend.practice.client.FollowUpQuestionGenerator.GeneratedQuestion;
import io.interviewcodi.backend.practice.client.SpeechToTextClient;
import io.interviewcodi.backend.practice.client.SpeechToTextClient.AudioFile;
import io.interviewcodi.backend.practice.client.SpeechToTextClient.Transcription;
import io.interviewcodi.backend.practice.client.SpeechToTextException;
import io.interviewcodi.backend.practice.domain.AiRequestKind;
import io.interviewcodi.backend.practice.domain.TranscriptionContext;
import io.interviewcodi.backend.practice.repository.AiRequestRepository.ClaimedRequest;
import io.interviewcodi.backend.practice.repository.RecordingRepository.TranscribeSource;
import io.interviewcodi.backend.practice.service.AiRequestQueueService.Claim;
import io.interviewcodi.backend.practice.service.AiRequestQueueService.Outcome;
import io.interviewcodi.backend.practice.storage.RecordingStorage;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
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
    // 답변 버전 본문 한도(DB 제약)와 같다. 3분 녹음은 보통 1,000자 안팎이다.
    private static final int MAX_TRANSCRIPT_LENGTH = 3_000;

    private final AiRequestQueueService queue;
    private final FeedbackGenerator feedbackGenerator;
    private final FollowUpQuestionGenerator followUpQuestionGenerator;
    private final SpeechToTextClient speechToTextClient;
    private final RecordingStorage recordingStorage;

    AiRequestProcessingService(AiRequestQueueService queue, FeedbackGenerator feedbackGenerator,
                               FollowUpQuestionGenerator followUpQuestionGenerator,
                               SpeechToTextClient speechToTextClient, RecordingStorage recordingStorage) {
        this.queue = queue;
        this.feedbackGenerator = feedbackGenerator;
        this.followUpQuestionGenerator = followUpQuestionGenerator;
        this.speechToTextClient = speechToTextClient;
        this.recordingStorage = recordingStorage;
    }

    /**
     * @return 요청 하나를 꺼냈으면 true(결과와 상관없이). 꺼낼 것이 없으면 false
     */
    public boolean processNext() {
        return processNext(EnumSet.allOf(AiRequestKind.class));
    }

    /** {@code kinds}에 든 종류만 꺼내 처리한다. 인식 전용 슬롯이 쓴다. */
    public boolean processNext(Set<AiRequestKind> kinds) {
        Optional<Claim> claim = queue.claim(kinds);
        if (claim.isEmpty()) {
            return false;
        }
        ClaimedRequest request = claim.get().request();
        if (request == null) {
            return true;
        }
        Outcome outcome;
        try {
            outcome = generateAndApply(request);
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

    private Outcome generateAndApply(ClaimedRequest request) {
        if (request.kind() == AiRequestKind.TRANSCRIBE) {
            return transcribeAndApply(request);
        }
        if (request.kind() == AiRequestKind.FEEDBACK) {
            GeneratedFeedback feedback = feedbackGenerator.generate(queue.loadFeedbackInput(request));
            return queue.applyFeedback(request, feedback);
        }
        GeneratedQuestion question = followUpQuestionGenerator.generate(queue.loadFollowUpInput(request));
        return queue.applyFollowUp(request, question);
    }

    /**
     * 녹음을 읽어 인식기에 보낸다(트랜잭션 밖). 빈 결과·너무 긴 결과는 다시 해도 같으므로 재시도하지 않는다.
     * 제공자 오류·제한 시간 초과는 다른 요청과 같은 재시도 규칙을 따른다.
     */
    private Outcome transcribeAndApply(ClaimedRequest request) {
        TranscribeSource source = queue.loadTranscribeSource(request);
        Transcription transcription;
        try {
            byte[] audio = recordingStorage.load(source.storageKey()).getContentAsByteArray();
            String filename = "answer." + source.storageKey().substring(source.storageKey().lastIndexOf('.') + 1);
            transcription = speechToTextClient.transcribe(new AudioFile(audio, filename, source.contentType(),
                    TranscriptionContext.INTERVIEW_ANSWER));
        } catch (IOException ex) {
            return queue.fail(request, "RECORDING_READ_FAILED");
        } catch (SpeechToTextException ex) {
            if (ex.reason().equals("EMPTY_TEXT")) {
                return queue.failWithoutRetry(request, ex.reason());
            }
            return queue.fail(request, ex.reason());
        }
        String text = transcription.text() == null ? "" : transcription.text().strip();
        if (text.isEmpty()) {
            return queue.failWithoutRetry(request, "EMPTY_TEXT");
        }
        if (text.codePointCount(0, text.length()) > MAX_TRANSCRIPT_LENGTH) {
            return queue.failWithoutRetry(request, "TRANSCRIPT_TOO_LONG");
        }
        return queue.applyTranscription(request, text);
    }
}
