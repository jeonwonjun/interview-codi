package io.interviewcodi.backend.practice.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import io.interviewcodi.backend.practice.dto.AiRequestResponse;
import io.interviewcodi.backend.practice.dto.AnswerAttemptResponse;
import io.interviewcodi.backend.practice.dto.AnswerRevisionResponse;
import io.interviewcodi.backend.practice.dto.FeedbackResponse;
import io.interviewcodi.backend.practice.dto.RecordingResponse;
import io.interviewcodi.backend.practice.repository.AiRequestRepository;
import io.interviewcodi.backend.practice.repository.AiRequestRepository.RequestRow;
import io.interviewcodi.backend.practice.repository.AiRequestRepository.TranscribeRow;
import io.interviewcodi.backend.practice.repository.AnswerRepository;
import io.interviewcodi.backend.practice.repository.AnswerRepository.AttemptRow;
import io.interviewcodi.backend.practice.repository.FeedbackRepository;
import io.interviewcodi.backend.practice.repository.FeedbackRepository.FeedbackRow;
import io.interviewcodi.backend.practice.repository.RecordingRepository;
import io.interviewcodi.backend.practice.repository.RecordingRepository.RecordingRow;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 답변 시도 응답을 만든다. 현재 버전·피드백·피드백 요청에 음성 시도의 녹음·인식 요청을 붙인다.
 * 연습 상세와 제출·교정 응답이 같은 모양을 쓰도록 한곳에 둔다. 연습의 소유자·삭제 확인은 호출하는 쪽이 한다.
 */
@Component
class AnswerAttemptReader {

    private final AnswerRepository answerRepository;
    private final AiRequestRepository aiRequestRepository;
    private final FeedbackRepository feedbackRepository;
    private final RecordingRepository recordingRepository;

    AnswerAttemptReader(AnswerRepository answerRepository, AiRequestRepository aiRequestRepository,
                        FeedbackRepository feedbackRepository, RecordingRepository recordingRepository) {
        this.answerRepository = answerRepository;
        this.aiRequestRepository = aiRequestRepository;
        this.feedbackRepository = feedbackRepository;
        this.recordingRepository = recordingRepository;
    }

    /** 질문 ID별 시도. 시도 번호 순서. */
    Map<Long, List<AnswerAttemptResponse>> readByQuestion(long practiceId) {
        Context context = loadContext(practiceId);
        return answerRepository.findAttemptsWithCurrentRevision(practiceId).stream()
                .sorted(Comparator.comparingInt(AttemptRow::attemptNo))
                .map(context::toResponse)
                .collect(groupingBy(AnswerAttemptResponse::questionId));
    }

    AnswerAttemptResponse read(long practiceId, long answerId) {
        AttemptRow attempt = answerRepository.findAttemptWithCurrentRevision(answerId).orElseThrow();
        return loadContext(practiceId).toResponse(attempt);
    }

    private Context loadContext(long practiceId) {
        return new Context(
                aiRequestRepository.findLatestFeedbackRequests(practiceId).stream()
                        .collect(toMap(RequestRow::answerRevisionId, Function.identity())),
                feedbackRepository.findByPractice(practiceId).stream()
                        .collect(toMap(FeedbackRow::answerRevisionId, Function.identity())),
                recordingRepository.findByPractice(practiceId).stream()
                        .collect(toMap(RecordingRow::answerId, Function.identity())),
                aiRequestRepository.findLatestTranscribeRequests(practiceId).stream()
                        .collect(toMap(TranscribeRow::recordingId, Function.identity())));
    }

    private record Context(Map<Long, RequestRow> feedbackRequestByRevision, Map<Long, FeedbackRow> feedbackByRevision,
                           Map<Long, RecordingRow> recordingByAnswer, Map<Long, TranscribeRow> transcribeByRecording) {

        AnswerAttemptResponse toResponse(AttemptRow attempt) {
            RequestRow request = attempt.hasRevision() ? feedbackRequestByRevision.get(attempt.revisionId()) : null;
            FeedbackRow feedback = attempt.hasRevision() ? feedbackByRevision.get(attempt.revisionId()) : null;
            RecordingRow recording = recordingByAnswer.get(attempt.id());
            TranscribeRow transcribe = recording == null ? null : transcribeByRecording.get(recording.id());
            return new AnswerAttemptResponse(attempt.id(), attempt.questionId(), attempt.attemptNo(),
                    attempt.inputMode().name(),
                    attempt.hasRevision()
                            ? new AnswerRevisionResponse(attempt.revisionId(), attempt.revisionNo(), attempt.content())
                            : null,
                    feedback == null ? null : new FeedbackResponse(feedback.id(), feedback.answerRevisionId(),
                            feedback.observation(), feedback.suggestion(), feedback.suggestionFocus(),
                            feedback.previousReview()),
                    request == null ? null : new AiRequestResponse(request.id(), request.status().name()),
                    recording == null ? null : new RecordingResponse(recording.id(), recording.durationMs()),
                    transcribe == null ? null : new AiRequestResponse(transcribe.id(), transcribe.status().name()));
        }
    }
}
