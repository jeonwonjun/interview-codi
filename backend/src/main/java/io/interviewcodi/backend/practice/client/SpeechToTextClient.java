package io.interviewcodi.backend.practice.client;

/**
 * 녹음 파일을 글로 옮기는 음성인식 제공자. 제공자를 바꿀 수 있게 생성기와 같은 방식으로 인터페이스만 쓴다.
 * 실패는 {@link SpeechToTextException}으로 알린다. 빈 결과도 실패로 본다(빈 답변을 평가하지 않는다).
 */
public interface SpeechToTextClient {

    Transcription transcribe(AudioFile audio);

    /** 보고서·로그에 남길 제공자와 모델 이름. */
    String name();

    /**
     * @param filename    확장자로 형식을 알리는 제공자가 있어 확장자를 포함한다
     * @param contentType 예: audio/webm, audio/mp4
     */
    record AudioFile(byte[] bytes, String filename, String contentType) {
    }

    /**
     * @param audioSeconds 제공자가 알려 준 녹음 길이. 알려 주지 않으면 null
     */
    record Transcription(String text, long latencyMillis, Double audioSeconds) {
    }
}
