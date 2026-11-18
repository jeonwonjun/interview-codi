package io.interviewcodi.backend.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.interviewcodi.backend.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

/**
 * 요청을 받은 주소는 {@code http://localhost:5173}(Vite 프록시를 거친 주소)으로 둔다.
 */
class OriginCheckFilterTest {

    /** 응답 본문 형식은 KakaoLoginFlowTest가 실제 서버로 확인한다. 여기서는 오류 코드의 상태만 쓴다. */
    private final OriginCheckFilter filter = new OriginCheckFilter((request, response, handler, ex) -> {
        response.setStatus(((BusinessException) ex).getErrorCode().getStatus().value());
        return new ModelAndView();
    });

    @ParameterizedTest
    @ValueSource(strings = { "GET", "HEAD", "OPTIONS" })
    @DisplayName("조회 요청은 Origin 없이 통과한다")
    void safeMethodsPassWithoutOrigin(String method) throws Exception {
        assertThat(passes(method, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "POST", "PUT", "PATCH", "DELETE" })
    @DisplayName("같은 출처의 변경 요청은 통과한다")
    void sameOriginPasses(String method) throws Exception {
        assertThat(passes(method, "http://localhost:5173")).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "null", // 샌드박스 iframe, 일부 리다이렉트
            "http://evil.example", // 다른 사이트
            "http://localhost:8080", // 같은 사이트의 다른 포트: SameSite=Lax가 막지 못한다
            "https://localhost:5173", // 다른 scheme
            "http://sub.localhost:5173", // 하위 도메인
            "not a uri" })
    @DisplayName("Origin이 없거나 다른 출처면 403으로 거부한다")
    void otherOriginsAreRejected(String origin) throws Exception {
        assertThat(passes("POST", origin)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({ "http, 80, http://app.example", "https, 443, https://app.example" })
    @DisplayName("Origin에서 생략된 기본 포트를 같은 포트로 본다")
    void defaultPortsAreOmittedInOrigin(String scheme, int port, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/logout");
        request.setScheme(scheme);
        request.setServerName("app.example");
        request.setServerPort(port);
        request.addHeader("Origin", origin);

        assertThat(passes(request)).isTrue();
    }

    private boolean passes(String method, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/logout");
        request.setServerName("localhost");
        request.setServerPort(5173);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return passes(request);
    }

    private boolean passes(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        boolean passed = chain.getRequest() != null;
        if (!passed) {
            assertThat(response.getStatus()).isEqualTo(403);
        }
        return passed;
    }
}
