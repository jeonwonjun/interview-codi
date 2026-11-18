package io.interviewcodi.backend.auth;

import io.interviewcodi.backend.auth.domain.LoginUser;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * 다른 패키지의 MockMvc 테스트가 로그인한 세션을 만들 때 쓴다. 카카오 로그인 흐름은 KakaoLoginFlowTest가 확인한다.
 * <p>
 * Spring Session 필터가 요청의 세션을 쿠키로 DB에서 찾으므로 MockMvc의 {@code session(...)}은 쓰이지 않는다.
 * 실제 JDBC 세션을 저장하고 그 세션 쿠키를 돌려준다.
 */
public final class TestLogin {

    private TestLogin() {
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Cookie sessionCookie(SessionRepository sessionRepository, long userId) {
        Session session = sessionRepository.createSession();
        session.setAttribute(LoginUser.SESSION_ATTRIBUTE, new LoginUser(userId));
        sessionRepository.save(session);
        // DefaultCookieSerializer는 세션 ID를 Base64로 인코딩해 쿠키에 담는다.
        String value = Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));
        return new Cookie("SESSION", value);
    }
}
