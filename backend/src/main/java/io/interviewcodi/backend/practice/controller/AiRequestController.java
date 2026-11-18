package io.interviewcodi.backend.practice.controller;

import io.interviewcodi.backend.auth.domain.LoginUser;
import io.interviewcodi.backend.practice.dto.AiRequestStatusResponse;
import io.interviewcodi.backend.practice.service.AiRequestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 화면이 처리 중인 AI 요청을 폴링하는 API. */
@RestController
class AiRequestController {

    private final AiRequestService aiRequestService;

    AiRequestController(AiRequestService aiRequestService) {
        this.aiRequestService = aiRequestService;
    }

    @GetMapping("/api/ai-requests/{requestId}")
    AiRequestStatusResponse get(LoginUser loginUser, @PathVariable long requestId) {
        return aiRequestService.get(loginUser.userId(), requestId);
    }
}
