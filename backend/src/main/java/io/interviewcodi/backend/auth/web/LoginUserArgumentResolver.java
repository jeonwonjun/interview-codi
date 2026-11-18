package io.interviewcodi.backend.auth.web;

import io.interviewcodi.backend.auth.domain.LoginUser;
import io.interviewcodi.backend.common.error.BusinessException;
import io.interviewcodi.backend.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 파라미터의 {@link LoginUser}를 세션에서 채운다.
 * 로그인 확인은 {@link LoginRequiredInterceptor}가 먼저 하지만, 확인 대상에서 빠진 경로에서 쓰여도 401이 되게 한다.
 */
class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == LoginUser.class;
    }

    @Override
    public LoginUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpSession session = (request != null) ? request.getSession(false) : null;
        if (session != null && session.getAttribute(LoginUser.SESSION_ATTRIBUTE) instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
    }
}
