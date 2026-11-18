package io.interviewcodi.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.interviewcodi.backend.TestcontainersConfiguration;
import io.interviewcodi.backend.user.domain.DeletedUserException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserAccountServiceTest {

    /** 테스트끼리 같은 DB를 공유하므로 테스트마다 다른 카카오 회원번호를 쓴다. */
    private static final AtomicLong NEXT_KAKAO_USER_ID = new AtomicLong(1_000_000L);

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("첫 로그인에 사용자를 만든다")
    void createsUserOnFirstLogin() {
        long kakaoUserId = NEXT_KAKAO_USER_ID.incrementAndGet();

        long userId = userAccountService.findOrCreateByKakaoUserId(kakaoUserId);

        assertThat(countUsers(kakaoUserId)).isEqualTo(1);
        assertThat(jdbcClient.sql("select kakao_user_id from users where id = :id")
                .param("id", userId)
                .query(Long.class)
                .single()).isEqualTo(kakaoUserId);
    }

    @Test
    @DisplayName("재로그인하면 같은 사용자를 돌려준다")
    void returnsSameUserOnRelogin() {
        long kakaoUserId = NEXT_KAKAO_USER_ID.incrementAndGet();

        long first = userAccountService.findOrCreateByKakaoUserId(kakaoUserId);
        long second = userAccountService.findOrCreateByKakaoUserId(kakaoUserId);

        assertThat(second).isEqualTo(first);
        assertThat(countUsers(kakaoUserId)).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 첫 로그인해도 사용자는 한 명만 생긴다")
    void concurrentFirstLoginsCreateOneUser() throws Exception {
        long kakaoUserId = NEXT_KAKAO_USER_ID.incrementAndGet();
        int requests = 16;
        CountDownLatch start = new CountDownLatch(1);
        Callable<Long> login = () -> {
            start.await();
            return userAccountService.findOrCreateByKakaoUserId(kakaoUserId);
        };

        List<Long> userIds;
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            List<Future<Long>> futures = IntStream.range(0, requests).mapToObj(i -> executor.submit(login)).toList();
            start.countDown();
            userIds = futures.stream().map(UserAccountServiceTest::await).toList();
        }

        assertThat(userIds).hasSize(requests).containsOnly(userIds.getFirst());
        assertThat(countUsers(kakaoUserId)).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제된 사용자의 로그인은 거부한다")
    void rejectsDeletedUser() {
        long kakaoUserId = NEXT_KAKAO_USER_ID.incrementAndGet();
        long userId = userAccountService.findOrCreateByKakaoUserId(kakaoUserId);
        jdbcClient.sql("update users set deleted_at = now() where id = :id").param("id", userId).update();

        assertThatThrownBy(() -> userAccountService.findOrCreateByKakaoUserId(kakaoUserId))
                .isInstanceOf(DeletedUserException.class);
        assertThat(countUsers(kakaoUserId)).isEqualTo(1);
    }

    @Test
    @DisplayName("DB가 중복 회원번호를 거부한다")
    void databaseRejectsDuplicateKakaoUserId() {
        long kakaoUserId = NEXT_KAKAO_USER_ID.incrementAndGet();
        userAccountService.findOrCreateByKakaoUserId(kakaoUserId);

        assertThatThrownBy(() -> jdbcClient.sql("insert into users (kakao_user_id) values (:k)")
                .param("k", kakaoUserId)
                .update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    private int countUsers(long kakaoUserId) {
        return jdbcClient.sql("select count(*) from users where kakao_user_id = :k")
                .param("k", kakaoUserId)
                .query(Integer.class)
                .single();
    }

    private static Long await(Future<Long> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
