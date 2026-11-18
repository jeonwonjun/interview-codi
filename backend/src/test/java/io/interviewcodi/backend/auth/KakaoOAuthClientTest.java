package io.interviewcodi.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 서버 대신 가짜 응답으로, 보내는 요청 형식과 응답 처리를 확인한다.
 */
class KakaoOAuthClientTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";

    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private MockRestServiceServer kakao;

    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        kakao = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoOAuthClient(builder, new KakaoProperties("client-id", "client-secret",
                URI.create("https://kauth.kakao.com/oauth/authorize"), URI.create(TOKEN_URI), URI.create(USER_INFO_URI)));
    }

    @Test
    @DisplayName("인가 코드를 PKCE와 함께 교환하고 회원번호만 읽는다")
    void exchangesCodeWithPkceAndReadsOnlyKakaoUserId() {
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("client_id", "client-id");
        expectedForm.add("client_secret", "client-secret");
        expectedForm.add("redirect_uri", "http://localhost:5173/login/oauth2/code/kakao");
        expectedForm.add("code", "auth-code");
        expectedForm.add("code_verifier", "verifier");
        kakao.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType("application/x-www-form-urlencoded;charset=UTF-8"))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess("""
                    {"token_type":"bearer","access_token":"access-token","expires_in":21599,
                     "refresh_token":"refresh-token","refresh_token_expires_in":5183999}
                    """, MediaType.APPLICATION_JSON));
        kakao.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("""
                    {"id":4200000001,"connected_at":"2026-11-14T00:00:00Z","properties":{"nickname":"홍길동"}}
                    """, MediaType.APPLICATION_JSON));

        long kakaoUserId = client.fetchKakaoUserId("auth-code", "verifier",
                "http://localhost:5173/login/oauth2/code/kakao");

        assertThat(kakaoUserId).isEqualTo(4_200_000_001L);
        kakao.verify();
    }

    @Test
    @DisplayName("토큰 요청 오류는 RestClientException이 된다")
    void tokenErrorIsRestClientException() {
        kakao.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"invalid_grant\",\"error_code\":\"KOE320\"}"));

        assertThatThrownBy(() -> client.fetchKakaoUserId("used-code", "verifier", "http://localhost/cb"))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    @DisplayName("액세스 토큰이 없는 응답은 거부한다")
    void rejectsTokenResponseWithoutAccessToken() {
        kakao.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchKakaoUserId("code", "verifier", "http://localhost/cb"))
                .isInstanceOf(KakaoLoginException.class)
                .hasMessage("missing_access_token");
    }

    @Test
    @DisplayName("회원번호가 없는 사용자 정보는 거부한다")
    void rejectsUserInfoWithoutKakaoUserId() {
        kakao.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"access-token\"}", MediaType.APPLICATION_JSON));
        kakao.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"connected_at\":\"2026-11-14T00:00:00Z\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchKakaoUserId("code", "verifier", "http://localhost/cb"))
                .isInstanceOf(KakaoLoginException.class)
                .hasMessage("missing_kakao_user_id");
    }

    @Test
    @DisplayName("숫자가 아닌 회원번호는 거부한다")
    void rejectsNonNumericKakaoUserId() {
        kakao.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"access-token\"}", MediaType.APPLICATION_JSON));
        kakao.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"id\":\"not-a-number\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchKakaoUserId("code", "verifier", "http://localhost/cb"))
                .isInstanceOf(RestClientException.class);
    }
}
