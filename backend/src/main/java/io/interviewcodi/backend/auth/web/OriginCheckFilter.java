package io.interviewcodi.backend.auth.web;

import io.interviewcodi.backend.common.error.BusinessException;
import io.interviewcodi.backend.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * CSRF 방어. 상태를 바꾸는 요청(GET·HEAD·OPTIONS·TRACE 외)은 {@code Origin}이 요청을 받은 주소와 같을 때만
 * 처리한다.
 * <p>
 * 세션 쿠키의 SameSite=Lax가 다른 사이트에서 보낸 POST에 쿠키를 싣지 않는 것이 1차 방어이고, 이 필터가 2차 방어다.
 * Lax는 같은 사이트(예: 같은 도메인의 다른 포트나 하위 도메인)에서 온 요청은 막지 못하므로 출처를 직접 비교한다.
 * <ul>
 * <li>브라우저는 상태를 바꾸는 요청에 Origin을 항상 보낸다. Origin이 없거나 {@code null}이면 거부한다.</li>
 * <li>비교 기준은 scheme·host·port다. 프록시 뒤에서 실행하면 전달 헤더 처리(server.forward-headers-strategy)를 함께
 * 설정해야 원래 주소로 비교된다.</li>
 * </ul>
 * 필터는 컨트롤러 밖에서 실행되므로, 거부 응답은 MVC의 예외 처리기에 넘겨 다른 API 오류와 같은 형식으로 만든다.
 */
@Component
class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final Logger log = LoggerFactory.getLogger(OriginCheckFilter.class);

    private final HandlerExceptionResolver exceptionResolver;

    OriginCheckFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || isSameOrigin(request.getHeader(HttpHeaders.ORIGIN), request)) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("Rejected {} {} from origin {}", request.getMethod(), request.getRequestURI(),
                request.getHeader(HttpHeaders.ORIGIN));
        exceptionResolver.resolveException(request, response, null,
                new BusinessException(ErrorCode.FORBIDDEN_ORIGIN));
    }

    static boolean isSameOrigin(String origin, HttpServletRequest request) {
        if (origin == null) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException ex) {
            return false;
        }
        // "null" Origin(샌드박스 iframe 등)은 scheme과 host가 없다.
        if (uri.getScheme() == null || uri.getHost() == null) {
            return false;
        }
        return uri.getScheme().equalsIgnoreCase(request.getScheme())
                && uri.getHost().equalsIgnoreCase(request.getServerName())
                && port(uri) == request.getServerPort();
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
