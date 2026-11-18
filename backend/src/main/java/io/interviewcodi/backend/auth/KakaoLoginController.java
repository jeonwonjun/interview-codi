package io.interviewcodi.backend.auth;

import io.interviewcodi.backend.user.DeletedUserException;
import io.interviewcodi.backend.user.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로그인(OAuth 2.0 Authorization Code + PKCE).
 * <ol>
 * <li>{@code GET /oauth2/authorization/kakao}: state와 code_verifier를 만들어 세션에 두고 카카오 인가 화면으로
 * 보낸다.</li>
 * <li>{@code GET /login/oauth2/code/kakao}: 카카오가 돌려준 state를 세션 값과 비교한 뒤, 인가 코드를 회원번호로
 * 바꾸고 내부 사용자를 찾거나 가입시킨다. 세션 ID를 새로 발급하고 로그인 사용자를 세션에 담는다.</li>
 * </ol>
 * 성공하면 {@code /}, 실패하면 {@code /?login=error}로 보낸다. 실패 이유는 화면에 노출하지 않고 로그로 남긴다.
 * <p>
 * 브라우저는 Vite 개발 서버의 프록시를 거쳐 같은 주소로 호출한다고 가정한다. redirect_uri는 요청이 들어온
 * 주소로 만들며, 카카오 콘솔에 등록된 주소만 카카오가 허용한다.
 */
@RestController
class KakaoLoginController {

    static final String LOGIN_START_PATH = "/oauth2/authorization/kakao";

    static final String CALLBACK_PATH = "/login/oauth2/code/kakao";

    private static final String PENDING_LOGIN_ATTRIBUTE = "kakaoPendingLogin";

    private static final URI LOGIN_SUCCESS_URI = URI.create("/");

    private static final URI LOGIN_ERROR_URI = URI.create("/?login=error");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Logger log = LoggerFactory.getLogger(KakaoLoginController.class);

    private final KakaoProperties properties;

    private final KakaoOAuthClient kakaoOAuthClient;

    private final UserAccountService userAccountService;

    KakaoLoginController(KakaoProperties properties, KakaoOAuthClient kakaoOAuthClient,
            UserAccountService userAccountService) {
        this.properties = properties;
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.userAccountService = userAccountService;
    }

    @GetMapping(LOGIN_START_PATH)
    ResponseEntity<Void> startLogin(HttpServletRequest request) {
        String redirectUri = ServletUriComponentsBuilder.fromContextPath(request).path(CALLBACK_PATH).toUriString();
        PendingLogin pending = new PendingLogin(randomToken(), randomToken(), redirectUri);
        // 로그인을 시작할 때만 세션을 만든다. 콜백에서 같은 브라우저인지 확인하는 데 쓴다.
        request.getSession().setAttribute(PENDING_LOGIN_ATTRIBUTE, pending);

        URI authorizationUri = UriComponentsBuilder.fromUri(properties.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", pending.state())
                .queryParam("code_challenge", codeChallenge(pending.codeVerifier()))
                .queryParam("code_challenge_method", "S256")
                .encode()
                .build()
                .toUri();
        return redirect(authorizationUri);
    }

    @GetMapping(CALLBACK_PATH)
    ResponseEntity<Void> handleCallback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String error,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        PendingLogin pending = takePendingLogin(session);

        // 이 브라우저에서 시작한 로그인인지 확인한다. 공격자가 자신의 인가 코드로 피해자를 로그인시키는 것을 막는다.
        if (pending == null || state == null || !constantTimeEquals(pending.state(), state)) {
            log.warn("Kakao login rejected: missing login start or state mismatch");
            return redirect(LOGIN_ERROR_URI);
        }
        // 사용자가 동의 화면에서 취소하면 error=access_denied로 돌아온다.
        if (error != null || code == null) {
            log.info("Kakao login not completed: error={}", error);
            return redirect(LOGIN_ERROR_URI);
        }

        long userId;
        try {
            long kakaoUserId = kakaoOAuthClient.fetchKakaoUserId(code, pending.codeVerifier(), pending.redirectUri());
            userId = userAccountService.findOrCreateByKakaoUserId(kakaoUserId);
        } catch (RestClientException | KakaoLoginException | DeletedUserException ex) {
            log.warn("Kakao login failed", ex);
            return redirect(LOGIN_ERROR_URI);
        }

        // 세션 고정 방어: 로그인 전 세션 ID를 공격자가 알고 있어도 로그인 후 세션을 쓰지 못하게 ID를 바꾼다.
        request.changeSessionId();
        session.setAttribute(LoginUser.SESSION_ATTRIBUTE, new LoginUser(userId));
        return redirect(LOGIN_SUCCESS_URI);
    }

    /** state는 한 번만 쓸 수 있도록 꺼내면서 지운다. */
    private static PendingLogin takePendingLogin(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object pending = session.getAttribute(PENDING_LOGIN_ATTRIBUTE);
        session.removeAttribute(PENDING_LOGIN_ATTRIBUTE);
        return (pending instanceof PendingLogin p) ? p : null;
    }

    /** 32바이트 난수를 Base64url로 표현한다. code_verifier 길이 조건(43~128자)도 만족한다. */
    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** PKCE S256: Base64url(SHA-256(code_verifier)). */
    static String codeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location.toString()).build();
    }

    /** 로그인 시작부터 콜백까지 세션에 보관하는 값. 세션이 DB에 직렬화되므로 Serializable이다. */
    record PendingLogin(String state, String codeVerifier, String redirectUri) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
