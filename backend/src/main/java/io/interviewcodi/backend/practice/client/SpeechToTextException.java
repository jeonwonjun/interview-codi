package io.interviewcodi.backend.practice.client;

/** 음성인식 실패. {@code reason}은 HTTP_429, TIMEOUT, EMPTY_TEXT처럼 원인을 짧게 담는다. */
public class SpeechToTextException extends RuntimeException {

    private final String reason;

    public SpeechToTextException(String reason, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
