package io.interviewcodi.backend.auth.web;

import io.interviewcodi.backend.auth.domain.LoginUser;
import io.interviewcodi.backend.common.error.BusinessException;
import io.interviewcodi.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인한 세션이 아니면 컨트롤러를 실행하지 않고 401({@code LOGIN_REQUIRED})을 반환한다. 로그인 화면으로 리다이렉트하지 않는다.
 * <p>
 * {@code getSession(false)}로 확인해, 로그인하지 않은 요청이 세션(=DB 행)을 만들지 않게 한다.
 */
class LoginRequiredInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(LoginUser.SESSION_ATTRIBUTE) instanceof LoginUser) {
            return true;
        }
        throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
    }
}
