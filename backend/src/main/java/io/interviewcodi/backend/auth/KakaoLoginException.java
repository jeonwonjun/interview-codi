package io.interviewcodi.backend.auth;

/**
 * 카카오 응답이 로그인에 필요한 값을 담고 있지 않을 때 발생한다.
 */
class KakaoLoginException extends RuntimeException {

    KakaoLoginException(String message) {
        super(message);
    }
}
