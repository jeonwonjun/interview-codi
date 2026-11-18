package io.interviewcodi.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.interviewcodi.backend.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 서버 호출만 가짜로 바꾸고, 실제 서버·Spring Session JDBC·PostgreSQL로 로그인부터 로그아웃까지 확인한다.
 * 세션 ID 교체와 쿠키 속성은 실제 서블릿 컨테이너에서만 드러나므로 MockMvc를 쓰지 않는다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class KakaoLoginFlowTest {

    private static final long KAKAO_USER_ID = 4_200_000_001L;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void closeClient() {
        http.close();
    }

    @Test
    @DisplayName("로그인 시작은 state와 PKCE를 담아 카카오 인가 화면으로 보낸다")
    void startRedirectsToKakaoWithStateAndPkce() throws Exception {
        LoginStart start = startLogin();

        URI kakao = start.location();
        assertThat(kakao.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(kakao.getPath()).isEqualTo("/oauth/authorize");
        assertThat(start.param("response_type")).isEqualTo("code");
        assertThat(start.param("client_id")).isNotBlank();
        assertThat(start.param("redirect_uri")).isEqualTo(baseUrl() + KakaoLoginController.CALLBACK_PATH);
        assertThat(start.param("state")).hasSize(43);
        assertThat(start.param("code_challenge")).hasSize(43);
        assertThat(start.param("code_challenge_method")).isEqualTo("S256");
        assertThat(start.params().containsKey("scope")).as("프로필 동의를 요청하지 않는다").isFalse();
    }

    @Test
    @DisplayName("로그인하면 내부 사용자가 생기고 세션 ID가 바뀐다")
    void loginCreatesInternalUserAndRotatesSessionId() throws Exception {
        LoginStart start = startLogin();
        when(kakaoOAuthClient.fetchKakaoUserId(eq("auth-code"), anyString(), eq(start.param("redirect_uri"))))
                .thenReturn(KAKAO_USER_ID);

        HttpResponse<Void> callback = callback("code=auth-code&state=" + start.param("state"), start.sessionCookie());

        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(callback.headers().firstValue("Location")).hasValue("/");
        String setCookie = setSessionCookie(callback).orElseThrow();
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax");
        String loggedInCookie = cookiePair(setCookie);
        assertThat(loggedInCookie).as("로그인하면 세션 ID가 바뀐다").isNotEqualTo(start.sessionCookie());

        // 인가 요청에 보낸 code_challenge가 토큰 요청의 code_verifier에서 나온 값이어야 한다.
        ArgumentCaptor<String> verifier = ArgumentCaptor.forClass(String.class);
        verify(kakaoOAuthClient).fetchKakaoUserId(eq("auth-code"), verifier.capture(), anyString());
        assertThat(KakaoLoginController.codeChallenge(verifier.getValue())).isEqualTo(start.param("code_challenge"));

        long userId = jdbcClient.sql("select id from users where kakao_user_id = :kakaoUserId")
                .param("kakaoUserId", KAKAO_USER_ID)
                .query(Long.class)
                .single();
        HttpResponse<String> me = getMe(loggedInCookie);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).isEqualTo("{\"id\":" + userId + "}");
        HttpResponse<String> oldSession = getMe(start.sessionCookie());
        assertThat(oldSession.statusCode()).as("로그인 전 세션 ID로는 접근할 수 없다").isEqualTo(401);
        assertThat(oldSession.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(oldSession.body()).contains("\"code\":\"LOGIN_REQUIRED\"").contains("\"instance\":\"/api/me\"");
    }

    @Test
    @DisplayName("state가 다르면 거부하고, 한 번 시도한 state는 다시 쓸 수 없다")
    void stateMustMatchAndCanBeUsedOnce() throws Exception {
        LoginStart start = startLogin();

        HttpResponse<Void> wrongState = callback("code=auth-code&state=forged", start.sessionCookie());
        HttpResponse<Void> replayed = callback("code=auth-code&state=" + start.param("state"), start.sessionCookie());

        assertThat(wrongState.headers().firstValue("Location")).hasValue("/?login=error");
        assertThat(replayed.headers().firstValue("Location")).as("틀린 state로 한 번 시도하면 그 로그인 시도는 끝난다")
                .hasValue("/?login=error");
        verifyNoInteractions(kakaoOAuthClient);
    }

    @Test
    @DisplayName("로그인 시작 없이 들어온 콜백은 거부한다")
    void callbackWithoutLoginStartIsRejected() throws Exception {
        HttpResponse<Void> callback = callback("code=auth-code&state=anything", null);

        assertThat(callback.headers().firstValue("Location")).hasValue("/?login=error");
        verifyNoInteractions(kakaoOAuthClient);
    }

    @Test
    @DisplayName("동의 화면에서 취소하면 로그인 실패 화면으로 보낸다")
    void canceledConsentRedirectsToLoginError() throws Exception {
        LoginStart start = startLogin();

        HttpResponse<Void> callback = callback("error=access_denied&state=" + start.param("state"),
                start.sessionCookie());

        assertThat(callback.headers().firstValue("Location")).hasValue("/?login=error");
        verifyNoInteractions(kakaoOAuthClient);
    }

    @Test
    @DisplayName("카카오 호출이 실패하면 로그인되지 않는다")
    void kakaoFailureDoesNotLogIn() throws Exception {
        LoginStart start = startLogin();
        when(kakaoOAuthClient.fetchKakaoUserId(anyString(), anyString(), anyString()))
                .thenThrow(new ResourceAccessException("timeout"));

        HttpResponse<Void> callback = callback("code=auth-code&state=" + start.param("state"), start.sessionCookie());

        assertThat(callback.headers().firstValue("Location")).hasValue("/?login=error");
        assertThat(getMe(start.sessionCookie()).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("로그아웃은 같은 출처에서만 되고 세션을 끝낸다")
    void logoutRequiresSameOriginAndEndsSession() throws Exception {
        String cookie = logIn();

        HttpResponse<String> noOrigin = logout(cookie, null);
        assertThat(noOrigin.statusCode()).isEqualTo(403);
        assertThat(noOrigin.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(noOrigin.body()).contains("\"code\":\"FORBIDDEN_ORIGIN\"");
        assertThat(logout(cookie, "http://evil.example").statusCode()).isEqualTo(403);
        assertThat(getMe(cookie).statusCode()).as("거부된 로그아웃은 세션을 건드리지 않는다").isEqualTo(200);

        HttpResponse<String> loggedOut = logout(cookie, baseUrl());

        assertThat(loggedOut.statusCode()).isEqualTo(204);
        assertThat(setSessionCookie(loggedOut)).hasValueSatisfying(c -> assertThat(c).contains("Max-Age=0"));
        assertThat(getMe(cookie).statusCode()).isEqualTo(401);
        assertThat(logout(null, baseUrl()).statusCode()).as("세션이 없어도 같은 결과").isEqualTo(204);
    }

    private String logIn() throws Exception {
        LoginStart start = startLogin();
        when(kakaoOAuthClient.fetchKakaoUserId(anyString(), anyString(), anyString())).thenReturn(KAKAO_USER_ID);
        HttpResponse<Void> callback = callback("code=auth-code&state=" + start.param("state"), start.sessionCookie());
        return cookiePair(setSessionCookie(callback).orElseThrow());
    }

    private LoginStart startLogin() throws Exception {
        HttpResponse<Void> response = http.send(request(KakaoLoginController.LOGIN_START_PATH, null).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(302);
        return new LoginStart(URI.create(response.headers().firstValue("Location").orElseThrow()),
                cookiePair(setSessionCookie(response).orElseThrow()));
    }

    private HttpResponse<Void> callback(String query, String cookie) throws Exception {
        HttpResponse<Void> response = http.send(request(KakaoLoginController.CALLBACK_PATH + "?" + query, cookie).build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(302);
        return response;
    }

    private HttpResponse<String> getMe(String cookie) throws Exception {
        return http.send(request("/api/me", cookie).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> logout(String cookie, String origin) throws Exception {
        HttpRequest.Builder builder = request("/api/logout", cookie).POST(HttpRequest.BodyPublishers.noBody());
        if (origin != null) {
            builder.header("Origin", origin);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path, String cookie) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return builder;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static Optional<String> setSessionCookie(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("SESSION=")).findFirst();
    }

    /** {@code SESSION=값; Path=/; HttpOnly} 중 요청에 다시 보낼 {@code SESSION=값} 부분. */
    private static String cookiePair(String setCookie) {
        return setCookie.split(";", 2)[0];
    }

    private record LoginStart(URI location, String sessionCookie) {

        MultiValueMap<String, String> params() {
            return UriComponentsBuilder.fromUri(location).build().getQueryParams();
        }

        String param(String name) {
            return params().getFirst(name);
        }
    }
}
