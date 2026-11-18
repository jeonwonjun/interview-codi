package io.interviewcodi.backend.auth;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 로그인 설정. 키는 {@code .env}의 {@code KAKAO_CLIENT_ID}, {@code KAKAO_CLIENT_SECRET}에서 읽는다.
 */
@ConfigurationProperties("kakao")
record KakaoProperties(String clientId, String clientSecret, URI authorizationUri, URI tokenUri,
        URI userInfoUri) {
}
