package io.interviewcodi.backend.practice.dto;

/**
 * 답변 시도 하나. 피드백과 요청 상태는 현재 버전의 것만 담는다. 교정 전 버전의 피드백은 넣지 않는다.
 *
 * @param inputMode            TEXT 또는 VOICE
 * @param currentRevision      현재 버전. 인식이 끝나지 않은 음성 시도는 null
 * @param feedback             현재 버전의 피드백. 아직 없으면 null
 * @param feedbackRequest      현재 버전의 가장 최근 피드백 요청. 없으면 null
 * @param recording            음성 시도의 녹음. 텍스트 시도이거나 녹음을 지웠으면 null
 * @param transcriptionRequest 녹음의 가장 최근 인식 요청. 텍스트 시도는 null
 */
public record AnswerAttemptResponse(
        long id,
        long questionId,
        int attemptNo,
        String inputMode,
        AnswerRevisionResponse currentRevision,
        FeedbackResponse feedback,
        AiRequestResponse feedbackRequest,
        RecordingResponse recording,
        AiRequestResponse transcriptionRequest
) {
}
