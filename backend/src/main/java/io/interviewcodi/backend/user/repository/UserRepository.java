package io.interviewcodi.backend.user.repository;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcClient jdbcClient;

    UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 같은 카카오 회원번호가 없을 때만 삽입한다. 다른 요청이 같은 회원번호를 삽입 중이면 PostgreSQL이 그
     * 트랜잭션의 결과를 기다린 뒤 충돌로 처리한다.
     *
     * @return 새로 만든 내부 사용자 ID. 이미 있으면 빈 값
     */
    public Optional<Long> insertIfAbsent(long kakaoUserId) {
        return jdbcClient.sql("""
                        insert into users (kakao_user_id)
                        values (:kakaoUserId)
                        on conflict (kakao_user_id) do nothing
                        returning id
                        """)
                .param("kakaoUserId", kakaoUserId)
                .query(Long.class)
                .optional();
    }

    /** 삭제된 사용자도 포함해 찾는다. 삭제 여부 판단은 서비스가 한다. */
    public Optional<UserStatus> findStatusByKakaoUserId(long kakaoUserId) {
        return jdbcClient.sql("""
                        select id, deleted_at is not null as deleted
                        from users
                        where kakao_user_id = :kakaoUserId
                        """)
                .param("kakaoUserId", kakaoUserId)
                .query(UserStatus.class)
                .optional();
    }

    public record UserStatus(long id, boolean deleted) {
    }
}
