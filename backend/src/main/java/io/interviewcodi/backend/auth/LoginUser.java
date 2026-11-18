package io.interviewcodi.backend.auth;

import java.io.Serial;
import java.io.Serializable;

/**
 * 로그인한 사용자. 세션에는 내부 사용자 ID만 담고 카카오 사용자 정보 응답은 보관하지 않는다.
 * <p>
 * 세션이 PostgreSQL에 직렬화되어 저장되므로 {@link Serializable}이어야 한다. 필드를 바꾸면 기존 세션을
 * 역직렬화하지 못할 수 있으니, 배포 시 재로그인이 필요한지 함께 확인한다.
 * 컨트롤러는 {@code LoginUser} 파라미터로 받아 {@link #userId()}만 업무 서비스에 전달한다.
 */
public record LoginUser(long userId) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 세션에서 로그인 사용자를 담는 속성 이름. 이 속성이 있으면 로그인한 세션이다. */
    static final String SESSION_ATTRIBUTE = "loginUser";
}
