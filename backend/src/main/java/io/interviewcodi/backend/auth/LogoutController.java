package io.interviewcodi.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션을 무효화한다. DB의 세션 행이 지워지고 세션 쿠키도 만료된다.
 * 다른 사이트가 로그아웃시키지 못하도록 {@link OriginCheckFilter}가 같은 출처의 요청만 통과시킨다.
 */
@RestController
class LogoutController {

    @PostMapping("/api/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
