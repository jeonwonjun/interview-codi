package io.interviewcodi.backend.auth.controller;

import io.interviewcodi.backend.auth.domain.LoginUser;
import io.interviewcodi.backend.auth.dto.MeResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프론트엔드가 로그인 여부를 확인하는 API. 로그인하지 않았으면 401을 받는다.
 */
@RestController
@RequestMapping("/api/me")
class MeController {

    @GetMapping
    ResponseEntity<MeResponse> me(LoginUser loginUser) {
        return ResponseEntity.ok(new MeResponse(loginUser.userId()));
    }
}
