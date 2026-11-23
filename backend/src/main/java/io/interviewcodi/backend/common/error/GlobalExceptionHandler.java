package io.interviewcodi.backend.common.error;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 업무 오류를 ProblemDetail(RFC 9457)에 {@code code}를 더해 응답한다. Spring이 던지는 요청 오류(400, 404 등)는
 * {@code spring.mvc.problemdetails.enabled}로 같은 형식이 되며 {@code code}는 없다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex) {
        return toResponse(ex.getErrorCode());
    }

    /** {@code spring.servlet.multipart.max-file-size}를 넘은 업로드. 컨트롤러에 닿기 전에 막힌다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return toResponse(ErrorCode.FILE_TOO_LARGE);
    }

    private static ResponseEntity<ProblemDetail> toResponse(ErrorCode errorCode) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
        problem.setProperty("code", errorCode.name());
        return ResponseEntity.status(errorCode.getStatus()).body(problem);
    }
}
