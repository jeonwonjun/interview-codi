package io.interviewcodi.backend.practice.client;

/**
 * AI 생성 실패. {@code errorType}은 요청 기록에 남기므로 답변 본문이나 프롬프트를 담지 않는다.
 */
public class AiGenerationException extends RuntimeException {

    private final String errorType;

    public AiGenerationException(String errorType, Throwable cause) {
        super(errorType, cause);
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }
}
