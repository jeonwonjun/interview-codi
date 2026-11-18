package io.interviewcodi.backend.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 인가 코드를 회원번호로 바꾸는 서버 간 호출.
 * <ol>
 * <li>토큰 받기: 인가 코드와 PKCE code_verifier로 액세스 토큰을 받는다.</li>
 * <li>사용자 정보 가져오기: 액세스 토큰으로 회원번호(id)만 읽는다.</li>
 * </ol>
 * 액세스 토큰은 이 호출 안에서만 쓰고 보관하지 않는다. 이후 카카오 API를 호출하는 기능이 없기 때문이다.
 * 카카오 응답이 늦어 로그인 요청이 오래 묶이지 않도록 연결·응답 시간을 제한한다.
 */
@Component
public class KakaoOAuthClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private static final MediaType FORM_UTF8 = new MediaType(MediaType.APPLICATION_FORM_URLENCODED,
            StandardCharsets.UTF_8);

    private final RestClient restClient;

    private final KakaoProperties properties;

    @Autowired
    KakaoOAuthClient(KakaoProperties properties) {
        this(RestClient.builder().requestFactory(requestFactory()), properties);
    }

    /** 테스트에서 가짜 카카오 서버를 연결한 builder를 넘긴다. */
    KakaoOAuthClient(RestClient.Builder restClientBuilder, KakaoProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    /**
     * @throws RestClientException 카카오가 오류를 응답하거나 연결·응답이 시간 안에 오지 않을 때
     * @throws KakaoLoginException 응답에 액세스 토큰이나 회원번호가 없을 때
     */
    public long fetchKakaoUserId(String code, String codeVerifier, String redirectUri) {
        return fetchKakaoUserId(requestAccessToken(code, codeVerifier, redirectUri));
    }

    private String requestAccessToken(String code, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        // 인가 요청에 보낸 redirect_uri와 같아야 카카오가 토큰을 발급한다.
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        form.add("code_verifier", codeVerifier);

        TokenResponse token = restClient.post()
                .uri(properties.tokenUri())
                .contentType(FORM_UTF8)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (token == null || !StringUtils.hasText(token.accessToken())) {
            throw new KakaoLoginException("missing_access_token");
        }
        return token.accessToken();
    }

    private long fetchKakaoUserId(String accessToken) {
        UserResponse user = restClient.get()
                .uri(properties.userInfoUri())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(UserResponse.class);
        // 회원번호가 없으면 가입시키지 않는다. 숫자가 아니면 응답 변환 단계에서 RestClientException이 난다.
        if (user == null || user.id() == null) {
            throw new KakaoLoginException("missing_kakao_user_id");
        }
        return user.id();
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    /** 닉네임 등 다른 항목은 읽지 않는다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserResponse(Long id) {
    }
}
