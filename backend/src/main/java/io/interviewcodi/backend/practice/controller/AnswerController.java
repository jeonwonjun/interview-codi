package io.interviewcodi.backend.practice.controller;

import io.interviewcodi.backend.auth.domain.LoginUser;
import io.interviewcodi.backend.practice.dto.AiRequestResponse;
import io.interviewcodi.backend.practice.dto.AnswerAttemptResponse;
import io.interviewcodi.backend.practice.dto.SubmitAnswerRequest;
import io.interviewcodi.backend.practice.service.AnswerService;
import io.interviewcodi.backend.practice.service.AnswerService.FeedbackRequestResult;
import io.interviewcodi.backend.practice.service.AnswerService.SubmitResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AnswerController {

    private final AnswerService answerService;

    AnswerController(AnswerService answerService) {
        this.answerService = answerService;
    }

    /** 새로 저장하면 201, 같은 {@code clientRequestId}를 다시 보낸 경우 처음 결과와 200. */
    @PostMapping("/api/questions/{questionId}/answers")
    ResponseEntity<AnswerAttemptResponse> submit(LoginUser loginUser, @PathVariable long questionId,
                                                 @RequestBody SubmitAnswerRequest request) {
        SubmitResult result = answerService.submit(loginUser.userId(), questionId, request.clientRequestId(),
                request.content());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.attempt());
    }

    /** 피드백 재요청. 실패한 경우에만 새로 만들어 201, 처리 중이거나 성공한 요청이 있으면 그것을 200. */
    @PostMapping("/api/answer-revisions/{revisionId}/feedback-requests")
    ResponseEntity<AiRequestResponse> requestFeedback(LoginUser loginUser, @PathVariable long revisionId) {
        FeedbackRequestResult result = answerService.requestFeedback(loginUser.userId(), revisionId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.request());
    }
}
