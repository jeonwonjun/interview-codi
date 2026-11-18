package io.interviewcodi.backend.auth;

import io.interviewcodi.backend.auth.dto.MeResponse;
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
    MeResponse me(LoginUser loginUser) {
        return new MeResponse(loginUser.userId());
    }
}
