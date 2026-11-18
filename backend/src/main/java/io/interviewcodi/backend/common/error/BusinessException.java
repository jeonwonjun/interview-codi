package io.interviewcodi.backend.common.error;

/**
 * API로 나가는 업무 오류. {@link GlobalExceptionHandler}가 ProblemDetail로 바꾼다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
