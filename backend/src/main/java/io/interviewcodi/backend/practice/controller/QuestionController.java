package io.interviewcodi.backend.practice.controller;

import io.interviewcodi.backend.auth.domain.LoginUser;
import io.interviewcodi.backend.practice.dto.AiRequestResponse;
import io.interviewcodi.backend.practice.dto.CreateFollowUpRequest;
import io.interviewcodi.backend.practice.dto.QuestionResponse;
import io.interviewcodi.backend.practice.service.QuestionService;
import io.interviewcodi.backend.practice.service.QuestionService.FollowUpRequestResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class QuestionController {

    private final QuestionService questionService;

    QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /** 꼬리질문 생성 요청. 새로 접수하면 202, 같은 버전의 요청이 처리 중이면 그것을 200. */
    @PostMapping("/api/practices/{practiceId}/follow-up-requests")
    ResponseEntity<AiRequestResponse> requestFollowUp(LoginUser loginUser, @PathVariable long practiceId,
                                                      @RequestBody CreateFollowUpRequest request) {
        FollowUpRequestResult result = questionService.requestFollowUp(loginUser.userId(), practiceId,
                request.answerRevisionId());
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result.request());
    }

    /** 질문 넘기기. 다음 고정 질문을 201로 돌려준다. 남은 고정 질문이 없으면 409 NO_MORE_QUESTIONS. */
    @PostMapping("/api/questions/{questionId}/skip")
    ResponseEntity<QuestionResponse> skip(LoginUser loginUser, @PathVariable long questionId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questionService.skip(loginUser.userId(), questionId));
    }
}
