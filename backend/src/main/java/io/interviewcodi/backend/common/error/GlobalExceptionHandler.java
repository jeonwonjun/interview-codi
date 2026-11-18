package io.interviewcodi.backend.common.error;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 업무 오류를 ProblemDetail(RFC 9457)에 {@code code}를 더해 응답한다. Spring이 던지는 요청 오류(400, 404 등)는
 * {@code spring.mvc.problemdetails.enabled}로 같은 형식이 되며 {@code code}는 없다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
        problem.setProperty("code", errorCode.name());
        return ResponseEntity.status(errorCode.getStatus()).body(problem);
    }
}
