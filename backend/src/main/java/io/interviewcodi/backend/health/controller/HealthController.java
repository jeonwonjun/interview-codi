package io.interviewcodi.backend.health.controller;

import io.interviewcodi.backend.health.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버가 HTTP 요청을 처리하는지 확인하는 개발용 API.
 * DB·외부 AI 연결 상태는 검사하지 않는다.
 */
@RestController
@RequestMapping("/api/health")
class HealthController {

    @GetMapping
    HealthResponse health() {
        return new HealthResponse("UP");
    }
}
